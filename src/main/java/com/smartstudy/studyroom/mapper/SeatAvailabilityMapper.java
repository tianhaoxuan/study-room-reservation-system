package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SeatAvailabilityMapper {

    @Select("""
            <script>
            SELECT DISTINCT
                r.id,
                r.user_id,
                r.seat_id,
                r.room_id,
                r.reservation_date,
                r.time_slot,
                r.start_time,
                r.end_time,
                r.status,
                r.sign_time,
                r.leave_time,
                r.create_time
            FROM reservation_slot_occupancy o
            JOIN reservation r
              ON o.reservation_id = r.id
            WHERE o.room_id = #{roomId}
              AND o.reservation_date = #{reservationDate}
              AND o.slot_id IN
              <foreach collection="slotIds" item="slotId"
                       open="(" separator="," close=")">
                  #{slotId}
              </foreach>
              AND r.status IN (1, 2)
            </script>
            """)
    List<Reservation> findActiveReservationsBySlotIds(
            @Param("roomId") Long roomId,
            @Param("reservationDate") LocalDate reservationDate,
            @Param("slotIds") List<Long> slotIds
    );
}
