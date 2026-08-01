package com.smartstudy.studyroom;

import com.smartstudy.studyroom.service.ReservationTimeoutService;
import com.smartstudy.studyroom.task.ReservationTimeoutTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReservationTimeoutTaskTest {

    @Test
    void shouldRunWindowedCompensationScan() {
        ReservationTimeoutService service =
                mock(ReservationTimeoutService.class);
        ReservationTimeoutTask task =
                new ReservationTimeoutTask(
                        service,
                        50,
                        Duration.ofHours(24)
                );

        task.compensateTimeoutReservations();

        verify(service).compensateExpiredReservations(
                50,
                Duration.ofHours(24)
        );
    }
}