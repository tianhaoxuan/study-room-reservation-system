package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.RedisSeatOccupancyBitmapBatchConsistencyService;
import com.smartstudy.studyroom.task.RedisSeatOccupancyBitmapConsistencyTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisSeatOccupancyBitmapConsistencyTaskTest {

    @Test
    void shouldRunBatchReconcileFromToday() {
        RedisSeatOccupancyBitmapBatchConsistencyService service =
                mock(RedisSeatOccupancyBitmapBatchConsistencyService.class);

        RedisSeatOccupancyBitmapConsistencyTask task =
                new RedisSeatOccupancyBitmapConsistencyTask(
                        service,
                        2,
                        50
                );

        task.reconcileProjection();

        verify(service).reconcileFrom(
                LocalDate.now(),
                2,
                50
        );
    }
}