package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.ReservationSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalTime;
import java.util.List;

@Mapper
public interface ReservationSlotMapper {

    @Select("""
            SELECT id, slot_code, slot_name, start_time, end_time,
                   enabled, display_order, create_time, update_time
            FROM reservation_slot
            WHERE enabled = 1
              AND start_time >= #{openTime}
              AND end_time <= #{closeTime}
            ORDER BY display_order
            """)
    List<ReservationSlot> findEnabledWithin(
            @Param("openTime") LocalTime openTime,
            @Param("closeTime") LocalTime closeTime
    );

    @Select("""
            SELECT id, slot_code, slot_name, start_time, end_time,
                   enabled, display_order, create_time, update_time
            FROM reservation_slot
            WHERE id = #{id}
              AND enabled = 1
            """)
    ReservationSlot findEnabledById(@Param("id") Long id);

    @Select("""
            SELECT id, slot_code, slot_name, start_time, end_time,
                   enabled, display_order, create_time, update_time
            FROM reservation_slot
            WHERE enabled = 1
              AND display_order BETWEEN #{startOrder} AND #{endOrder}
            ORDER BY display_order
            """)
    List<ReservationSlot> findEnabledByDisplayOrderRange(
            @Param("startOrder") Integer startOrder,
            @Param("endOrder") Integer endOrder
    );

    @Select("""
            SELECT id
            FROM reservation_slot
            WHERE enabled = 1
            ORDER BY display_order
            """)
    List<Long> findEnabledSlotIds();
}