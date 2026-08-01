package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.config.RabbitMqConfig;
import com.smartstudy.studyroom.messaging.CheckinTimeoutMessage;
import com.smartstudy.studyroom.service.ReservationTimeoutMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "debug=false",
        "spring.sql.init.mode=never",
        "logging.level.root=warn",
        "logging.level.org.springframework=warn",
        "logging.level.org.springframework.jdbc=warn",
        "logging.level.org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler=off",
        "logging.level.com.smartstudy.studyroom.mapper=warn",
        "studyroom.timeout-scan.enabled=false",
        "studyroom.rabbitmq.checkin-timeout.enabled=true",
        "studyroom.rabbitmq.checkin-timeout.listener-auto-startup=true",
        "studyroom.rabbitmq.checkin-timeout.dead-letter-listener-auto-startup=false"
})
@Testcontainers(disabledWithoutDocker = true)
class RabbitMqCheckinTimeoutIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("studyroom_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                MYSQL::getDriverClassName
        );

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add(
                "spring.rabbitmq.username",
                RABBITMQ::getAdminUsername
        );
        registry.add(
                "spring.rabbitmq.password",
                RABBITMQ::getAdminPassword
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry listenerEndpointRegistry;

    @BeforeEach
    void setUp() {
        stopAllRabbitListeners();
        purgeRabbitQueues();

        jdbcTemplate.update("ALTER TABLE violation MODIFY reason VARCHAR(255)");
        jdbcTemplate.update("DELETE FROM reservation_timeout_message");
        jdbcTemplate.update("DELETE FROM reservation_slot_occupancy");
        jdbcTemplate.update("DELETE FROM violation");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM seat");
        jdbcTemplate.update("DELETE FROM study_room");
        jdbcTemplate.update("DELETE FROM building");
        jdbcTemplate.update("DELETE FROM `user`");
        jdbcTemplate.update("DELETE FROM system_config");

        jdbcTemplate.update("""
                INSERT INTO system_config(config_key, config_value, description)
                VALUES('violation_limit', '3', 'max violations before ban')
                """);
        jdbcTemplate.update("""
                INSERT INTO building(id, building_name, status)
                VALUES(1, 'RabbitMQ Building', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO study_room(
                    id,
                    building_id,
                    room_name,
                    total_seats,
                    reserved_seats,
                    occupancy_rate,
                    open_time,
                    close_time,
                    status
                )
                VALUES(1, 1, 'Room A', 1, 0, 0.00, '08:00:00', '22:30:00', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO seat(
                    id,
                    room_id,
                    seat_no,
                    x,
                    y,
                    has_power,
                    near_window,
                    status
                )
                VALUES(1, 1, 'A001', 1, 1, 1, 0, ?)
                """, BizConstants.SEAT_STATUS_FREE);
        jdbcTemplate.update("""
                INSERT INTO `user`(
                    id,
                    openid,
                    student_no,
                    real_name,
                    credit_score,
                    violation_count,
                    status
                )
                VALUES(1, 'openid-1', '20240001', 'Student 1', 100, 0, ?)
                """, BizConstants.USER_STATUS_NORMAL);
        jdbcTemplate.update("""
                INSERT INTO reservation(
                    id,
                    user_id,
                    seat_id,
                    room_id,
                    reservation_date,
                    time_slot,
                    start_time,
                    end_time,
                    status
                )
                VALUES(1001, 1, 1, 1, ?, '08:00-10:00',
                       '08:00:00', '10:00:00', ?)
                """,
                LocalDate.now(),
                BizConstants.RESERVATION_PENDING);
        jdbcTemplate.update("""
                INSERT INTO reservation_slot_occupancy(
                    reservation_id,
                    user_id,
                    seat_id,
                    room_id,
                    reservation_date,
                    slot_id
                )
                VALUES(1001, 1, 1, 1, ?, 2)
                """,
                LocalDate.now());

        startRabbitListener(RabbitMqConfig.CHECKIN_TIMEOUT_LISTENER_ID);
    }

    @AfterEach
    void tearDown() {
        stopAllRabbitListeners();
        jdbcTemplate.update("ALTER TABLE violation MODIFY reason VARCHAR(255)");
        purgeRabbitQueues();
    }

    @Test
    void rabbitMqDelayedMessageMarksPendingReservationViolated()
            throws Exception {

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_EXCHANGE,
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_ROUTING_KEY,
                new CheckinTimeoutMessage(
                        1001L,
                        LocalDateTime.now().minusSeconds(1)
                ),
                message -> {
                    message.getMessageProperties().setExpiration("300");
                    return message;
                }
        );

        assertEventually(() -> {
            assertThat(reservationStatus(1001L))
                    .isEqualTo(BizConstants.RESERVATION_VIOLATED);
            assertThat(countRows("reservation_slot_occupancy"))
                    .isZero();
            assertThat(countRows("violation"))
                    .isEqualTo(1);
            assertThat(userViolationCount(1L))
                    .isEqualTo(1);
        });
    }

    @Test
    void failedTimeoutHandlingShouldRouteMessageToFailureQueue()
            throws Exception {

        jdbcTemplate.update("ALTER TABLE violation MODIFY reason VARCHAR(1)");

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_EXCHANGE,
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_ROUTING_KEY,
                new CheckinTimeoutMessage(
                        1001L,
                        LocalDateTime.now().minusSeconds(1)
                ),
                message -> {
                    message.getMessageProperties().setExpiration("300");
                    return message;
                }
        );

        org.springframework.amqp.core.Message failedMessage =
                rabbitTemplate.receive(
                        RabbitMqConfig.CHECKIN_TIMEOUT_FAILURE_QUEUE,
                        10000
                );

        assertThat(failedMessage).isNotNull();
        assertThat(reservationStatus(1001L))
                .isEqualTo(BizConstants.RESERVATION_PENDING);
        assertThat(countRows("reservation_slot_occupancy"))
                .isEqualTo(1);
        assertThat(countRows("violation"))
                .isZero();
    }

    @Test
    void deadLetterConsumerShouldMarkOutboxMessageDeadLetter()
            throws Exception {

        LocalDateTime deadlineAt = LocalDateTime.now().minusSeconds(1);
        jdbcTemplate.update("""
                INSERT INTO reservation_timeout_message(
                    id,
                    reservation_id,
                    deadline_at,
                    status,
                    next_retry_time
                )
                VALUES(2001, 1001, ?, ?, NULL)
                """,
                deadlineAt,
                ReservationTimeoutMessageService.STATUS_SENT
        );

        jdbcTemplate.update("ALTER TABLE violation MODIFY reason VARCHAR(1)");

        startRabbitListener(
                RabbitMqConfig.CHECKIN_TIMEOUT_DEAD_LETTER_LISTENER_ID
        );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_EXCHANGE,
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_ROUTING_KEY,
                new CheckinTimeoutMessage(2001L, 1001L, deadlineAt),
                message -> {
                    message.getMessageProperties().setExpiration("300");
                    return message;
                }
        );

        assertEventually(() -> {
            assertThat(timeoutMessageStatus(2001L))
                    .isEqualTo(
                            ReservationTimeoutMessageService.STATUS_DEAD_LETTER
                    );
            assertThat(timeoutMessageDeadLetterTime(2001L))
                    .isNotNull();
        });

        assertThat(reservationStatus(1001L))
                .isEqualTo(BizConstants.RESERVATION_PENDING);
        assertThat(countRows("reservation_slot_occupancy"))
                .isEqualTo(1);
        assertThat(countRows("violation"))
                .isZero();
    }

    @Test
    void duplicateTimeoutMessagesShouldBeHandledIdempotently()
            throws Exception {

        CheckinTimeoutMessage message = new CheckinTimeoutMessage(
                1001L,
                LocalDateTime.now().minusSeconds(1)
        );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_EXCHANGE,
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_ROUTING_KEY,
                message,
                rawMessage -> {
                    rawMessage.getMessageProperties().setExpiration("300");
                    return rawMessage;
                }
        );
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_EXCHANGE,
                RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_ROUTING_KEY,
                message,
                rawMessage -> {
                    rawMessage.getMessageProperties().setExpiration("300");
                    return rawMessage;
                }
        );

        assertEventually(() -> {
            assertThat(reservationStatus(1001L))
                    .isEqualTo(BizConstants.RESERVATION_VIOLATED);
            assertThat(countRows("reservation_slot_occupancy"))
                    .isZero();
            assertThat(countRows("violation"))
                    .isEqualTo(1);
            assertThat(userViolationCount(1L))
                    .isEqualTo(1);
        });

        Thread.sleep(1000);

        assertThat(reservationStatus(1001L))
                .isEqualTo(BizConstants.RESERVATION_VIOLATED);
        assertThat(countRows("reservation_slot_occupancy"))
                .isZero();
        assertThat(countRows("violation"))
                .isEqualTo(1);
        assertThat(userViolationCount(1L))
                .isEqualTo(1);
    }

    private void purgeRabbitQueues() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(RabbitMqConfig.CHECKIN_TIMEOUT_DELAY_QUEUE);
            channel.queuePurge(RabbitMqConfig.CHECKIN_TIMEOUT_QUEUE);
            channel.queuePurge(RabbitMqConfig.CHECKIN_TIMEOUT_FAILURE_QUEUE);
            return null;
        });
    }

    private void startRabbitListener(String listenerId) {
        MessageListenerContainer container =
                listenerEndpointRegistry.getListenerContainer(listenerId);
        if (container != null && !container.isRunning()) {
            container.start();
        }
    }

    private void stopAllRabbitListeners() {
        listenerEndpointRegistry.getListenerContainers()
                .forEach(container -> {
                    if (container.isRunning()) {
                        container.stop();
                    }
                });
    }

    private void assertEventually(CheckedAssertion assertion)
            throws Exception {

        AssertionError lastError = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try {
                assertion.check();
                return;
            } catch (AssertionError e) {
                lastError = e;
                Thread.sleep(200);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    private Integer reservationStatus(Long reservationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM reservation WHERE id = ?",
                Integer.class,
                reservationId
        );
    }

    private Integer timeoutMessageStatus(Long messageId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM reservation_timeout_message WHERE id = ?",
                Integer.class,
                messageId
        );
    }

    private LocalDateTime timeoutMessageDeadLetterTime(Long messageId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT dead_letter_time
                FROM reservation_timeout_message
                WHERE id = ?
                """,
                LocalDateTime.class,
                messageId
        );
    }

    private Integer userViolationCount(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT violation_count FROM `user` WHERE id = ?",
                Integer.class,
                userId
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    @FunctionalInterface
    private interface CheckedAssertion {

        void check() throws Exception;
    }
}