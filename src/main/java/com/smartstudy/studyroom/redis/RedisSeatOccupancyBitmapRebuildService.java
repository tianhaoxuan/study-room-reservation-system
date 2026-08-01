package com.smartstudy.studyroom.redis;

import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class RedisSeatOccupancyBitmapRebuildService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RedisSeatOccupancyBitmapRebuildService.class
            );

    private final ReservationSlotOccupancyMapper occupancyMapper;
    private final SeatOccupancyBitmapService bitmapService;
    private final RedisSeatOccupancyBitmapRebuildLockService lockService;

    public RedisSeatOccupancyBitmapRebuildService(
            ReservationSlotOccupancyMapper occupancyMapper,
            SeatOccupancyBitmapService bitmapService,
            RedisSeatOccupancyBitmapRebuildLockService lockService) {

        this.occupancyMapper = occupancyMapper;
        this.bitmapService = bitmapService;
        this.lockService = lockService;
    }

    public boolean rebuild(
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds) {

        if (roomId == null
                || reservationDate == null
                || slotIds == null
                || slotIds.isEmpty()) {
            return false;
        }

        List<Long> orderedSlotIds =
                slotIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (orderedSlotIds.isEmpty()) {
            return false;
        }

        return lockService.runWithLock(
                roomId,
                reservationDate,
                orderedSlotIds,
                () -> rebuildWithoutLock(
                        roomId,
                        reservationDate,
                        orderedSlotIds
                )
        );
    }

    private boolean rebuildWithoutLock(
            Long roomId,
            LocalDate reservationDate,
            List<Long> orderedSlotIds) {

        try {
            List<ReservationSlotOccupancy> occupancies =
                    occupancyMapper.findByRoomDateAndSlotIds(
                            roomId,
                            reservationDate,
                            orderedSlotIds
                    );

            Map<Long, Set<Long>> occupiedSeatIdsBySlot =
                    occupiedSeatIdsBySlot(
                            orderedSlotIds,
                            occupancies
                    );

            boolean rebuiltAll = true;

            for (Long slotId : orderedSlotIds) {
                boolean rebuilt =
                        bitmapService.rebuildSlot(
                                roomId,
                                reservationDate,
                                slotId,
                                occupiedSeatIdsBySlot.get(slotId)
                        );
                rebuiltAll = rebuiltAll && rebuilt;
            }

            return rebuiltAll;
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to rebuild Redis seat occupancy projection, roomId={}, reservationDate={}, slotIds={}",
                    roomId,
                    reservationDate,
                    orderedSlotIds,
                    ex
            );
            return false;
        }
    }

    private Map<Long, Set<Long>> occupiedSeatIdsBySlot(
            List<Long> slotIds,
            List<ReservationSlotOccupancy> occupancies) {

        Map<Long, Set<Long>> result =
                new LinkedHashMap<>();

        for (Long slotId : slotIds) {
            result.put(slotId, new LinkedHashSet<>());
        }

        for (ReservationSlotOccupancy occupancy : occupancies) {
            Set<Long> occupiedSeatIds =
                    result.get(occupancy.getSlotId());
            if (occupiedSeatIds != null) {
                occupiedSeatIds.add(occupancy.getSeatId());
            }
        }

        return result;
    }
}