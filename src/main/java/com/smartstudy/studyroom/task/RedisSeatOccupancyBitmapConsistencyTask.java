package com.smartstudy.studyroom.task;

import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapConsistencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "studyroom.redis.seat-occupancy.consistency.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisSeatOccupancyBitmapConsistencyTask {

    private final RedisSeatOccupancyBitmapConsistencyService
            consistencyService;
    private final Long roomId;
    private final List<Long> slotIds;

    public RedisSeatOccupancyBitmapConsistencyTask(
            RedisSeatOccupancyBitmapConsistencyService consistencyService,
            @Value("${studyroom.redis.seat-occupancy.consistency.default-room-id:1}")
            Long roomId,
            @Value("${studyroom.redis.seat-occupancy.consistency.default-slot-ids:1,2,3,4,5,6,7,8,9,10}")
            List<Long> slotIds) {

        this.consistencyService = consistencyService;
        this.roomId = roomId;
        this.slotIds = slotIds;
    }

    @Scheduled(
            fixedDelayString =
                    "${studyroom.redis.seat-occupancy.consistency.fixed-delay-ms:300000}"
    )
    public void reconcileTodayProjection() {
        consistencyService.reconcile(
                roomId,
                LocalDate.now(),
                slotIds
        );
    }
}