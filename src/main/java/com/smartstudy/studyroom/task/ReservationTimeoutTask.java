package com.smartstudy.studyroom.task;

import com.smartstudy.studyroom.service.ReservationTimeoutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(
        name = "studyroom.timeout-scan.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationTimeoutTask {

    private final ReservationTimeoutService reservationTimeoutService;
    private final int batchSize;
    private final Duration lookBack;

    public ReservationTimeoutTask(
            ReservationTimeoutService reservationTimeoutService,
            @Value("${studyroom.timeout-scan.batch-size:100}")
            int batchSize,
            @Value("${studyroom.timeout-scan.look-back:24h}")
            Duration lookBack) {

        this.reservationTimeoutService = reservationTimeoutService;
        this.batchSize = batchSize;
        this.lookBack = lookBack;
    }

    @Scheduled(
            fixedDelayString =
                    "${studyroom.timeout-scan.fixed-delay-ms:60000}"
    )
    public void compensateTimeoutReservations() {
        reservationTimeoutService.compensateExpiredReservations(
                batchSize,
                lookBack
        );
    }
}