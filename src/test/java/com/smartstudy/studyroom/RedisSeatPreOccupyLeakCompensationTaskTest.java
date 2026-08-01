package com.smartstudy.studyroom;

import com.smartstudy.studyroom.redis.RedisSeatPreOccupyLeakCompensationService;
import com.smartstudy.studyroom.task.RedisSeatPreOccupyLeakCompensationTask;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisSeatPreOccupyLeakCompensationTaskTest {

    @Test
    void shouldRunLeakCompensationWithConfiguredBatchSize() {
        RedisSeatPreOccupyLeakCompensationService service =
                mock(RedisSeatPreOccupyLeakCompensationService.class);

        RedisSeatPreOccupyLeakCompensationTask task =
                new RedisSeatPreOccupyLeakCompensationTask(
                        service,
                        50
                );

        task.compensateLeaks();

        verify(service).compensate(50);
    }
}