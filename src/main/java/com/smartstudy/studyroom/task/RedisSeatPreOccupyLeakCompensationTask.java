package com.smartstudy.studyroom.task;

import com.smartstudy.studyroom.redis.RedisSeatPreOccupyLeakCompensationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "studyroom.redis.seat-preoccupy.compensation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisSeatPreOccupyLeakCompensationTask {

    private final RedisSeatPreOccupyLeakCompensationService
            compensationService;
    private final int batchSize;

    public RedisSeatPreOccupyLeakCompensationTask(
            RedisSeatPreOccupyLeakCompensationService compensationService,
            @Value("${studyroom.redis.seat-preoccupy.compensation.batch-size:100}")
            int batchSize) {

        this.compensationService = compensationService;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${studyroom.redis.seat-preoccupy.compensation.fixed-delay-ms:60000}"
    )
    public void compensateLeaks() {
        compensationService.compensate(batchSize);
    }
}