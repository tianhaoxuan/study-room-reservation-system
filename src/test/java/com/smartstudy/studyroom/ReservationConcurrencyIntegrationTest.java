package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.dto.CreateReservationRequest;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "logging.level.com.smartstudy.studyroom.mapper=warn"
})
@Testcontainers(disabledWithoutDocker = true)
class ReservationConcurrencyIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 8;

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("studyroom_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                MYSQL::getDriverClassName
        );
    }

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM reservation_slot_occupancy");
        jdbcTemplate.update("DELETE FROM violation");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM seat");
        jdbcTemplate.update("DELETE FROM study_room");
        jdbcTemplate.update("DELETE FROM building");
        jdbcTemplate.update("DELETE FROM `user`");

        jdbcTemplate.update("""
                INSERT INTO building(id, building_name, status)
                VALUES(1, 'Concurrency Building', 1)
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

        for (int i = 1; i <= CONCURRENT_REQUESTS; i++) {
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
                    VALUES(?, ?, ?, ?, 100, 0, ?)
                    """,
                    i,
                    "openid-" + i,
                    "2024" + String.format("%04d", i),
                    "Student " + i,
                    BizConstants.USER_STATUS_NORMAL
            );
        }
    }

    @Test
    void concurrentReservationsForSameSeatAndSlotsAllowOnlyOneSuccess()
            throws Exception {

        LocalDate reservationDate = LocalDate.now().plusDays(1);
        ExecutorService executorService =
                Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyGate =
                new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (long userId = 1; userId <= CONCURRENT_REQUESTS; userId++) {
                futures.add(executorService.submit(createTask(
                        userId,
                        reservationDate,
                        readyGate,
                        startGate
                )));
            }

            assertThat(readyGate.await(10, TimeUnit.SECONDS))
                    .isTrue();
            startGate.countDown();

            long successCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(countRows("reservation"))
                    .isEqualTo(1);
            assertThat(countRows("reservation_slot_occupancy"))
                    .isEqualTo(4);
            assertThat(countDistinctSeatDateSlots())
                    .isEqualTo(4);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void databaseRejectsDuplicateSeatDateSlotOccupancy() {
        LocalDate reservationDate = LocalDate.now().plusDays(1);
        insertReservation(1001L, 1L, reservationDate);
        insertReservation(1002L, 2L, reservationDate);
        insertOccupancy(1001L, 1L, 1L, reservationDate, 2L);

        assertThatThrownBy(() ->
                insertOccupancy(1002L, 2L, 1L, reservationDate, 2L)
        ).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void databaseRejectsDuplicateUserDateSlotOccupancy() {
        LocalDate reservationDate = LocalDate.now().plusDays(1);
        insertReservation(1001L, 1L, reservationDate);
        insertReservation(1002L, 1L, reservationDate);
        insertOccupancy(1001L, 1L, 1L, reservationDate, 2L);

        assertThatThrownBy(() ->
                insertOccupancy(1002L, 1L, 2L, reservationDate, 2L)
        ).isInstanceOf(DuplicateKeyException.class);
    }

    private Callable<Boolean> createTask(
            Long userId,
            LocalDate reservationDate,
            CountDownLatch readyGate,
            CountDownLatch startGate) {

        return () -> {
            readyGate.countDown();
            startGate.await();

            try {
                reservationService.createReservation(
                        userId,
                        request(reservationDate)
                );
                return true;
            } catch (BusinessException e) {
                return false;
            }
        };
    }

    private CreateReservationRequest request(LocalDate reservationDate) {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setSeatId(1L);
        request.setRoomId(1L);
        request.setReservationDate(reservationDate);
        request.setStartSlotId(2L);
        request.setEndSlotId(5L);
        return request;
    }

    private void insertReservation(
            Long reservationId,
            Long userId,
            LocalDate reservationDate) {

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
                VALUES(?, ?, ?, 1, ?, '08:00-10:00', '08:00:00', '10:00:00', ?)
                """,
                reservationId,
                userId,
                userId,
                reservationDate,
                BizConstants.RESERVATION_PENDING
        );
    }

    private void insertOccupancy(
            Long reservationId,
            Long userId,
            Long seatId,
            LocalDate reservationDate,
            Long slotId) {

        jdbcTemplate.update("""
                INSERT INTO reservation_slot_occupancy(
                    reservation_id,
                    user_id,
                    seat_id,
                    room_id,
                    reservation_date,
                    slot_id
                )
                VALUES(?, ?, ?, 1, ?, ?)
                """,
                reservationId,
                userId,
                seatId,
                reservationDate,
                slotId
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countDistinctSeatDateSlots() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT DISTINCT seat_id, reservation_date, slot_id
                    FROM reservation_slot_occupancy
                ) t
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }
}
