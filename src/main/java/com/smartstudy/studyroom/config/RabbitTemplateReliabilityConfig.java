package com.smartstudy.studyroom.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RabbitTemplateReliabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(
            RabbitTemplateReliabilityConfig.class
    );

    private final RabbitTemplate rabbitTemplate;

    public RabbitTemplateReliabilityConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    public void configureRabbitTemplateCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(this::handleConfirm);
        rabbitTemplate.setReturnsCallback(this::handleReturned);
    }

    private void handleConfirm(
            CorrelationData correlationData,
            boolean ack,
            String cause) {

        String correlationId = correlationData == null
                ? null
                : correlationData.getId();

        if (ack) {
            log.debug(
                    "RabbitMQ message confirmed, correlationId={}",
                    correlationId
            );
            return;
        }

        log.warn(
                "RabbitMQ message was not confirmed, correlationId={}, cause={}",
                correlationId,
                cause
        );
    }

    private void handleReturned(ReturnedMessage returnedMessage) {
        log.warn(
                "RabbitMQ message returned, exchange={}, routingKey={}, " +
                        "replyCode={}, replyText={}",
                returnedMessage.getExchange(),
                returnedMessage.getRoutingKey(),
                returnedMessage.getReplyCode(),
                returnedMessage.getReplyText()
        );
    }
}