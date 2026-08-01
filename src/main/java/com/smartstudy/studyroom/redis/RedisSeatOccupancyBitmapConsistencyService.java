package com.smartstudy.studyroom.redis;

import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class RedisSeatOccupancyBitmapConsistencyService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RedisSeatOccupancyBitmapConsistencyService.class
            );

    private final SeatMapper seatMapper;
    private final ReservationSlotOccupancyMapper occupancyMapper;
    private final SeatOccupancyBitmapService bitmapService;
    private final RedisSeatOccupancyBitmapRebuildService rebuildService;
    private final boolean enabled;

    public RedisSeatOccupancyBitmapConsistencyService(
            SeatMapper seatMapper,
            ReservationSlotOccupancyMapper occupancyMapper,
            SeatOccupancyBitmapService bitmapService,
            RedisSeatOccupancyBitmapRebuildService rebuildService,
            @Value("${studyroom.redis.seat-occupancy.consistency.enabled:true}")
            boolean enabled) {

        this.seatMapper = seatMapper;
        this.occupancyMapper = occupancyMapper;
        this.bitmapService = bitmapService;
        this.rebuildService = rebuildService;
        this.enabled = enabled;
    }

    public ReconcileResult reconcile(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds) {

        if (!enabled) {
            return ReconcileResult.skipped("disabled");
        }
        if (roomId == null
                || reservationDate == null
                || slotIds == null
                || slotIds.isEmpty()) {
            return ReconcileResult.skipped("invalid input");
        }

        List<Long> orderedSlotIds = slotIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (orderedSlotIds.isEmpty()) {
            return ReconcileResult.skipped("empty slot ids");
        }

        try {
            Set<Long> roomSeatIds = roomSeatIds(roomId);
            if (roomSeatIds.isEmpty()) {
                return ReconcileResult.skipped("room has no seats");
            }

            Set<Long> mysqlOccupiedSeatIds =
                    mysqlOccupiedSeatIds(
                            roomId,
                            reservationDate,
                            orderedSlotIds
                    );

            Optional<Set<Long>> redisOccupiedSeatIds =
                    bitmapService.findOccupiedSeatIds(
                            roomId,
                            reservationDate,
                            orderedSlotIds,
                            roomSeatIds
                    );

            if (redisOccupiedSeatIds.isEmpty()) {
                boolean rebuilt = rebuildService.rebuild(
                        roomId,
                        reservationDate,
                        orderedSlotIds
                );
                return ReconcileResult.rebuilt(
                        "redis projection missing",
                        rebuilt,
                        mysqlOccupiedSeatIds,
                        Set.of()
                );
            }

            Set<Long> redisSeatIds = redisOccupiedSeatIds.get();
            if (mysqlOccupiedSeatIds.equals(redisSeatIds)) {
                return ReconcileResult.consistent(
                        mysqlOccupiedSeatIds,
                        redisSeatIds
                );
            }

            boolean rebuilt = rebuildService.rebuild(
                    roomId,
                    reservationDate,
                    orderedSlotIds
            );
            return ReconcileResult.rebuilt(
                    "redis projection inconsistent",
                    rebuilt,
                    mysqlOccupiedSeatIds,
                    redisSeatIds
            );
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to reconcile Redis seat occupancy bitmap, roomId={}, reservationDate={}, slotIds={}",
                    roomId,
                    reservationDate,
                    orderedSlotIds,
                    ex
            );
            return ReconcileResult.failed("exception: " + ex.getMessage());
        }
    }

    private Set<Long> roomSeatIds(Long roomId) {
        List<Seat> seats = seatMapper.findByRoomId(roomId);
        Set<Long> seatIds = new LinkedHashSet<>();

        if (seats == null) {
            return seatIds;
        }

        for (Seat seat : seats) {
            if (seat.getId() != null) {
                seatIds.add(seat.getId());
            }
        }
        return seatIds;
    }

    private Set<Long> mysqlOccupiedSeatIds(
            Long roomId,
            LocalDate reservationDate,
            List<Long> slotIds) {

        List<ReservationSlotOccupancy> occupancies =
                occupancyMapper.findByRoomDateAndSlotIds(
                        roomId,
                        reservationDate,
                        slotIds
                );

        Set<Long> seatIds = new LinkedHashSet<>();
        if (occupancies == null) {
            return seatIds;
        }

        for (ReservationSlotOccupancy occupancy : occupancies) {
            if (occupancy.getSeatId() != null) {
                seatIds.add(occupancy.getSeatId());
            }
        }
        return seatIds;
    }

    public record ReconcileResult(
            boolean checked,
            boolean consistent,
            boolean rebuilt,
            String reason,
            Set<Long> mysqlOccupiedSeatIds,
            Set<Long> redisOccupiedSeatIds) {

        public static ReconcileResult skipped(String reason) {
            return new ReconcileResult(
                    false,
                    false,
                    false,
                    reason,
                    Set.of(),
                    Set.of()
            );
        }

        public static ReconcileResult consistent(
                Set<Long> mysqlOccupiedSeatIds,
                Set<Long> redisOccupiedSeatIds) {

            return new ReconcileResult(
                    true,
                    true,
                    false,
                    "consistent",
                    mysqlOccupiedSeatIds,
                    redisOccupiedSeatIds
            );
        }

        public static ReconcileResult rebuilt(
                String reason,
                boolean rebuilt,
                Set<Long> mysqlOccupiedSeatIds,
                Set<Long> redisOccupiedSeatIds) {

            return new ReconcileResult(
                    true,
                    false,
                    rebuilt,
                    reason,
                    mysqlOccupiedSeatIds,
                    redisOccupiedSeatIds
            );
        }

        public static ReconcileResult failed(String reason) {
            return new ReconcileResult(
                    true,
                    false,
                    false,
                    reason,
                    Set.of(),
                    Set.of()
            );
        }
    }
}