package com.smartstudy.studyroom.task;

import com.smartstudy.studyroom.service.ReservationTimeoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "studyroom.timeout-scan.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationTimeoutTask {

    private final ReservationTimeoutService reservationTimeoutService;

    public ReservationTimeoutTask(
            ReservationTimeoutService reservationTimeoutService) {

        this.reservationTimeoutService = reservationTimeoutService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void releaseTimeoutReservations() {
        reservationTimeoutService.releaseTimeoutReservations();
    }
}