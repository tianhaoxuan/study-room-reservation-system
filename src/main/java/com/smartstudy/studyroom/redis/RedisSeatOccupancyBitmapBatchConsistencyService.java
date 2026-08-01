package com.smartstudy.studyroom.redis;

import com.smartstudy.studyroom.mapper.ReservationSlotMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class RedisSeatOccupancyBitmapBatchConsistencyService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RedisSeatOccupancyBitmapBatchConsistencyService.class
            );

    private final StudyRoomMapper studyRoomMapper;
    private final ReservationSlotMapper reservationSlotMapper;
    private final RedisSeatOccupancyBitmapConsistencyService consistencyService;

    public RedisSeatOccupancyBitmapBatchConsistencyService(
            StudyRoomMapper studyRoomMapper,
            ReservationSlotMapper reservationSlotMapper,
            RedisSeatOccupancyBitmapConsistencyService consistencyService) {

        this.studyRoomMapper = studyRoomMapper;
        this.reservationSlotMapper = reservationSlotMapper;
        this.consistencyService = consistencyService;
    }

    public BatchReconcileResult reconcileFrom(
            LocalDate startDate,
            int dateWindowDays,
            int roomLimit) {

        if (startDate == null) {
            return BatchReconcileResult.empty("invalid start date");
        }

        int normalizedDateWindowDays =
                normalizeDateWindowDays(dateWindowDays);
        int normalizedRoomLimit = normalizeRoomLimit(roomLimit);

        List<Long> roomIds = studyRoomMapper.findActiveRoomIds();
        List<Long> slotIds = reservationSlotMapper.findEnabledSlotIds();

        if (roomIds == null || roomIds.isEmpty()) {
            return BatchReconcileResult.empty("no active rooms");
        }
        if (slotIds == null || slotIds.isEmpty()) {
            return BatchReconcileResult.empty("no enabled slots");
        }

        List<Long> limitedRoomIds = roomIds.stream()
                .limit(normalizedRoomLimit)
                .toList();

        int checked = 0;
        int consistent = 0;
        int rebuilt = 0;
        int skipped = 0;
        int failed = 0;

        for (int dayOffset = 0;
             dayOffset < normalizedDateWindowDays;
             dayOffset++) {

            LocalDate reservationDate = startDate.plusDays(dayOffset);

            for (Long roomId : limitedRoomIds) {
                RedisSeatOccupancyBitmapConsistencyService.ReconcileResult result =
                        consistencyService.reconcile(
                                roomId,
                                reservationDate,
                                slotIds
                        );

                if (!result.checked()) {
                    skipped++;
                    continue;
                }

                checked++;

                if (result.consistent()) {
                    consistent++;
                }
                if (result.rebuilt()) {
                    rebuilt++;
                }
                if (!result.consistent() && !result.rebuilt()) {
                    failed++;
                }

                if (!result.consistent()) {
                    log.info(
                            "Redis seat bitmap reconcile result, roomId={}, reservationDate={}, reason={}, rebuilt={}",
                            roomId,
                            reservationDate,
                            result.reason(),
                            result.rebuilt()
                    );
                }
            }
        }

        return new BatchReconcileResult(
                limitedRoomIds.size(),
                normalizedDateWindowDays,
                checked,
                consistent,
                rebuilt,
                skipped,
                failed,
                "completed"
        );
    }

    private int normalizeDateWindowDays(int dateWindowDays) {
        if (dateWindowDays <= 0) {
            return 1;
        }
        return dateWindowDays;
    }

    private int normalizeRoomLimit(int roomLimit) {
        if (roomLimit <= 0) {
            return Integer.MAX_VALUE;
        }
        return roomLimit;
    }

    public record BatchReconcileResult(
            int rooms,
            int dates,
            int checked,
            int consistent,
            int rebuilt,
            int skipped,
            int failed,
            String reason) {

        public static BatchReconcileResult empty(String reason) {
            return new BatchReconcileResult(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    reason
            );
        }
    }
}