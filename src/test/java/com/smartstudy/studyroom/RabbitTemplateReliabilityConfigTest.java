package com.smartstudy.studyroom;

import com.smartstudy.studyroom.config.RabbitTemplateReliabilityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitTemplateReliabilityConfigTest {

    @Test
    void shouldConfigureRabbitTemplateReliabilityCallbacks() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitTemplateReliabilityConfig config =
                new RabbitTemplateReliabilityConfig(rabbitTemplate);

        config.configureRabbitTemplateCallbacks();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RabbitTemplate.ReturnsCallback> returnsCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ReturnsCallback.class);

        verify(rabbitTemplate).setMandatory(true);
        verify(rabbitTemplate).setConfirmCallback(confirmCaptor.capture());
        verify(rabbitTemplate).setReturnsCallback(returnsCaptor.capture());

        assertThat(confirmCaptor.getValue()).isNotNull();
        assertThat(returnsCaptor.getValue()).isNotNull();
    }
}