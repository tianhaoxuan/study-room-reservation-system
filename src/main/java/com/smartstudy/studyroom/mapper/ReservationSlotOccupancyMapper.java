package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReservationSlotOccupancyMapper {

    @Insert("""
            <script>
            INSERT INTO reservation_slot_occupancy(
                reservation_id,
                user_id,
                seat_id,
                room_id,
                reservation_date,
                slot_id
            )
            VALUES
            <foreach collection="occupancies" item="item" separator=",">
                (
                    #{item.reservationId},
                    #{item.userId},
                    #{item.seatId},
                    #{item.roomId},
                    #{item.reservationDate},
                    #{item.slotId}
                )
            </foreach>
            </script>
            """)
    int batchInsert(
            @Param("occupancies")
            List<ReservationSlotOccupancy> occupancies
    );

    @Select("""
            SELECT *
            FROM reservation_slot_occupancy
            WHERE reservation_id = #{reservationId}
            ORDER BY slot_id ASC
            """)
    List<ReservationSlotOccupancy> findByReservationId(
            @Param("reservationId") Long reservationId
    );

    @Delete("""
            DELETE FROM reservation_slot_occupancy
            WHERE reservation_id = #{reservationId}
            """)
    int deleteByReservationId(
            @Param("reservationId") Long reservationId
    );

    @Select("""
            <script>
            SELECT *
            FROM reservation_slot_occupancy
            WHERE room_id = #{roomId}
              AND reservation_date = #{reservationDate}
              AND slot_id IN
              <foreach collection="slotIds" item="slotId"
                       open="(" separator="," close=")">
                  #{slotId}
              </foreach>
            </script>
            """)
    List<ReservationSlotOccupancy> findByRoomDateAndSlotIds(
            @Param("roomId") Long roomId,
            @Param("reservationDate") LocalDate reservationDate,
            @Param("slotIds") List<Long> slotIds
    );

    @Select("""
            SELECT COUNT(DISTINCT o.seat_id)
            FROM reservation_slot_occupancy o
            JOIN reservation r
              ON o.reservation_id = r.id
            WHERE o.room_id = #{roomId}
              AND r.status IN (1, 2)
            """)
    int countDistinctActiveSeatsByRoomId(
            @Param("roomId") Long roomId
    );
}