package com.smartstudy.studyroom.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {

    public static final String CHECKIN_TIMEOUT_DELAY_EXCHANGE =
            "studyroom.reservation.checkin.delay.exchange";
    public static final String CHECKIN_TIMEOUT_EVENT_EXCHANGE =
            "studyroom.reservation.checkin.event.exchange";
    public static final String CHECKIN_TIMEOUT_FAILURE_EXCHANGE =
            "studyroom.reservation.checkin.failure.exchange";

    public static final String CHECKIN_TIMEOUT_DELAY_QUEUE =
            "studyroom.reservation.checkin.timeout.delay.queue";
    public static final String CHECKIN_TIMEOUT_QUEUE =
            "studyroom.reservation.checkin.timeout.queue";
    public static final String CHECKIN_TIMEOUT_FAILURE_QUEUE =
            "studyroom.reservation.checkin.timeout.failure.queue";

    public static final String CHECKIN_TIMEOUT_DELAY_ROUTING_KEY =
            "reservation.checkin.timeout.delay";
    public static final String CHECKIN_TIMEOUT_ROUTING_KEY =
            "reservation.checkin.timeout";
    public static final String CHECKIN_TIMEOUT_FAILURE_ROUTING_KEY =
            "reservation.checkin.timeout.failure";

    @Bean
    public DirectExchange checkinTimeoutDelayExchange() {
        return new DirectExchange(CHECKIN_TIMEOUT_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange checkinTimeoutEventExchange() {
        return new DirectExchange(CHECKIN_TIMEOUT_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange checkinTimeoutFailureExchange() {
        return new DirectExchange(CHECKIN_TIMEOUT_FAILURE_EXCHANGE, true, false);
    }

    @Bean
    public Queue checkinTimeoutDelayQueue() {
        return new Queue(
                CHECKIN_TIMEOUT_DELAY_QUEUE,
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange",
                        CHECKIN_TIMEOUT_EVENT_EXCHANGE,
                        "x-dead-letter-routing-key",
                        CHECKIN_TIMEOUT_ROUTING_KEY
                )
        );
    }

    @Bean
    public Queue checkinTimeoutQueue() {
        return new Queue(
                CHECKIN_TIMEOUT_QUEUE,
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange",
                        CHECKIN_TIMEOUT_FAILURE_EXCHANGE,
                        "x-dead-letter-routing-key",
                        CHECKIN_TIMEOUT_FAILURE_ROUTING_KEY
                )
        );
    }

    @Bean
    public Queue checkinTimeoutFailureQueue() {
        return new Queue(CHECKIN_TIMEOUT_FAILURE_QUEUE, true);
    }

    @Bean
    public Binding checkinTimeoutDelayBinding(
            Queue checkinTimeoutDelayQueue,
            DirectExchange checkinTimeoutDelayExchange) {

        return BindingBuilder.bind(checkinTimeoutDelayQueue)
                .to(checkinTimeoutDelayExchange)
                .with(CHECKIN_TIMEOUT_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding checkinTimeoutBinding(
            Queue checkinTimeoutQueue,
            DirectExchange checkinTimeoutEventExchange) {

        return BindingBuilder.bind(checkinTimeoutQueue)
                .to(checkinTimeoutEventExchange)
                .with(CHECKIN_TIMEOUT_ROUTING_KEY);
    }

    @Bean
    public Binding checkinTimeoutFailureBinding(
            Queue checkinTimeoutFailureQueue,
            DirectExchange checkinTimeoutFailureExchange) {

        return BindingBuilder.bind(checkinTimeoutFailureQueue)
                .to(checkinTimeoutFailureExchange)
                .with(CHECKIN_TIMEOUT_FAILURE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}