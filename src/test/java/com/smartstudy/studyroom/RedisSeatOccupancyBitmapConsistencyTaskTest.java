package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapConsistencyService;
import com.smartstudy.studyroom.task.RedisSeatOccupancyBitmapConsistencyTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisSeatOccupancyBitmapConsistencyTaskTest {

    @Test
    void shouldReconcileTodayDefaultRoomAndSlots() {
        RedisSeatOccupancyBitmapConsistencyService service =
                mock(RedisSeatOccupancyBitmapConsistencyService.class);
        RedisSeatOccupancyBitmapConsistencyTask task =
                new RedisSeatOccupancyBitmapConsistencyTask(
                        service,
                        1L,
                        List.of(2L, 3L)
                );

        task.reconcileTodayProjection();

        verify(service).reconcile(
                1L,
                LocalDate.now(),
                List.of(2L, 3L)
        );
    }
}