package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.dto.AdminReservationResponse;
import com.smartstudy.studyroom.dto.MyReservationResponse;
import com.smartstudy.studyroom.entity.Reservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface ReservationMapper {

    @Insert("""
            INSERT INTO reservation(
                user_id,
                seat_id,
                room_id,
                reservation_date,
                time_slot,
                start_time,
                end_time,
                status
            )
            VALUES(
                #{userId},
                #{seatId},
                #{roomId},
                #{reservationDate},
                #{timeSlot},
                #{startTime},
                #{endTime},
                #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Reservation reservation);

    @Select("""
            SELECT *
            FROM reservation
            WHERE id = #{id}
            """)
    Reservation findById(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE seat_id = #{seatId}
              AND reservation_date = #{reservationDate}
              AND status IN (1, 2)
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    int countSeatConflict(
            @Param("seatId") Long seatId,
            @Param("reservationDate") LocalDate reservationDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE user_id = #{userId}
              AND reservation_date = #{reservationDate}
              AND status IN (1, 2)
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    int countUserSlotConflict(
            @Param("userId") Long userId,
            @Param("reservationDate") LocalDate reservationDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE user_id = #{userId}
              AND reservation_date = #{reservationDate}
              AND status IN (1, 2)
            """)
    int countUserDailyActive(
            @Param("userId") Long userId,
            @Param("reservationDate") LocalDate reservationDate
    );

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE seat_id = #{seatId}
              AND status IN (1, 2)
            """)
    int countActiveBySeat(@Param("seatId") Long seatId);

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE seat_id = #{seatId}
              AND id <> #{reservationId}
              AND status IN (1, 2)
            """)
    int countActiveBySeatExclude(
            @Param("seatId") Long seatId,
            @Param("reservationId") Long reservationId
    );

    @Update("""
            UPDATE reservation
            SET status = #{status}
            WHERE id = #{id}
              AND status = #{oldStatus}
            """)
    int updateStatusIfCurrent(
            @Param("id") Long id,
            @Param("oldStatus") Integer oldStatus,
            @Param("status") Integer status
    );

    @Update("""
            UPDATE reservation
            SET status = #{status}
            WHERE id = #{id}
              AND status IN (1, 2)
            """)
    int updateActiveStatus(
            @Param("id") Long id,
            @Param("status") Integer status
    );

    @Update("""
            UPDATE reservation
            SET status = 2,
                sign_time = #{signTime}
            WHERE id = #{id}
              AND status = 1
            """)
    int markSigned(
            @Param("id") Long id,
            @Param("signTime") LocalDateTime signTime
    );

    @Update("""
            UPDATE reservation
            SET status = 3,
                leave_time = #{leaveTime}
            WHERE id = #{id}
              AND status = 2
            """)
    int markLeft(
            @Param("id") Long id,
            @Param("leaveTime") LocalDateTime leaveTime
    );

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE user_id = #{userId}
              AND (#{status} IS NULL OR status = #{status})
            """)
    long countMy(
            @Param("userId") Long userId,
            @Param("status") Integer status
    );

    @Select("""
            SELECT
                r.id AS reservation_id,
                b.building_name,
                sr.room_name,
                s.seat_no,
                r.reservation_date,
                r.time_slot,
                r.start_time,
                r.end_time,
                r.status
            FROM reservation r
            JOIN seat s
              ON r.seat_id = s.id
            JOIN study_room sr
              ON r.room_id = sr.id
            LEFT JOIN building b
              ON sr.building_id = b.id
            WHERE r.user_id = #{userId}
              AND (#{status} IS NULL OR r.status = #{status})
            ORDER BY
                r.reservation_date DESC,
                r.start_time DESC,
                r.id DESC
            LIMIT #{pageSize}
            OFFSET #{offset}
            """)
    List<MyReservationResponse> findMy(
            @Param("userId") Long userId,
            @Param("status") Integer status,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize
    );

    @Select("""
            SELECT *
            FROM reservation
            WHERE status = 1
            """)
    List<Reservation> findAllPending();

    @Select("""
            SELECT *
            FROM reservation
            WHERE room_id = #{roomId}
              AND reservation_date = #{reservationDate}
              AND time_slot = #{timeSlot}
              AND status IN (1, 2)
            """)
    List<Reservation> findActiveByRoomAndSlot(
            @Param("roomId") Long roomId,
            @Param("reservationDate") LocalDate reservationDate,
            @Param("timeSlot") String timeSlot
    );

    /*
     * MyBatis动态SQL保留普通字符串拼接形式。
     * 不使用Java文本块，避免IDEA将<script>和<if>
     * 错误识别为纯SQL或普通XML。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM reservation r " +
            "JOIN `user` u ON r.user_id = u.id " +
            "WHERE 1 = 1 " +
            "<if test='studentNo != null and studentNo != \"\"'>" +
            " AND u.student_no = #{studentNo}" +
            "</if> " +
            "<if test='roomId != null'>" +
            " AND r.room_id = #{roomId}" +
            "</if> " +
            "<if test='status != null'>" +
            " AND r.status = #{status}" +
            "</if> " +
            "<if test='reservationDate != null and reservationDate != \"\"'>" +
            " AND r.reservation_date = #{reservationDate}" +
            "</if>" +
            "</script>")
    long countAdmin(
            @Param("studentNo") String studentNo,
            @Param("roomId") Long roomId,
            @Param("status") Integer status,
            @Param("reservationDate") String reservationDate
    );

    @Select("<script>" +
            "SELECT " +
            "r.id AS reservation_id, " +
            "u.student_no, " +
            "u.real_name, " +
            "b.building_name, " +
            "sr.room_name, " +
            "s.seat_no, " +
            "r.reservation_date, " +
            "r.time_slot, " +
            "r.start_time, " +
            "r.end_time, " +
            "r.status " +
            "FROM reservation r " +
            "JOIN `user` u ON r.user_id = u.id " +
            "JOIN seat s ON r.seat_id = s.id " +
            "JOIN study_room sr ON r.room_id = sr.id " +
            "LEFT JOIN building b ON sr.building_id = b.id " +
            "WHERE 1 = 1 " +
            "<if test='studentNo != null and studentNo != \"\"'>" +
            " AND u.student_no = #{studentNo}" +
            "</if> " +
            "<if test='roomId != null'>" +
            " AND r.room_id = #{roomId}" +
            "</if> " +
            "<if test='status != null'>" +
            " AND r.status = #{status}" +
            "</if> " +
            "<if test='reservationDate != null and reservationDate != \"\"'>" +
            " AND r.reservation_date = #{reservationDate}" +
            "</if> " +
            "ORDER BY r.create_time DESC, r.id DESC " +
            "LIMIT #{pageSize} OFFSET #{offset}" +
            "</script>")
    List<AdminReservationResponse> findAdmin(
            @Param("studentNo") String studentNo,
            @Param("roomId") Long roomId,
            @Param("status") Integer status,
            @Param("reservationDate") String reservationDate,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize
    );
}