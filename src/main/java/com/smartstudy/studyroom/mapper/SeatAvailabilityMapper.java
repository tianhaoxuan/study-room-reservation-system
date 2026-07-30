package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface SeatAvailabilityMapper {

    @Select("""
            SELECT *
            FROM reservation
            WHERE room_id = #{roomId}
              AND reservation_date = #{reservationDate}
              AND status IN (1, 2)
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    List<Reservation> findActiveReservations(
            @Param("roomId") Long roomId,
            @Param("reservationDate") LocalDate reservationDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );
}