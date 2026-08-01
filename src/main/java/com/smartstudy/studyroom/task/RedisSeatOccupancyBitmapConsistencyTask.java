package com.smartstudy.studyroom.task;

import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapBatchConsistencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(
        name = "studyroom.redis.seat-occupancy.consistency.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisSeatOccupancyBitmapConsistencyTask {

    private final RedisSeatOccupancyBitmapBatchConsistencyService
            batchConsistencyService;
    private final int dateWindowDays;
    private final int roomLimit;

    public RedisSeatOccupancyBitmapConsistencyTask(
            RedisSeatOccupancyBitmapBatchConsistencyService
                    batchConsistencyService,
            @Value("${studyroom.redis.seat-occupancy.consistency.date-window-days:2}")
            int dateWindowDays,
            @Value("${studyroom.redis.seat-occupancy.consistency.room-limit:50}")
            int roomLimit) {

        this.batchConsistencyService = batchConsistencyService;
        this.dateWindowDays = dateWindowDays;
        this.roomLimit = roomLimit;
    }

    @Scheduled(
            fixedDelayString =
                    "${studyroom.redis.seat-occupancy.consistency.fixed-delay-ms:300000}"
    )
    public void reconcileProjection() {
        batchConsistencyService.reconcileFrom(
                LocalDate.now(),
                dateWindowDays,
                roomLimit
        );
    }
}