package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SeatMapper {

    @Select("SELECT * FROM seat WHERE id = #{id}")
    Seat findById(@Param("id") Long id);

    @Select("SELECT * FROM seat WHERE room_id = #{roomId} ORDER BY seat_no ASC")
    List<Seat> findByRoomId(@Param("roomId") Long roomId);

    @Select("SELECT COUNT(*) FROM seat WHERE room_id = #{roomId} AND seat_no = #{seatNo}")
    long countByRoomAndSeatNo(@Param("roomId") Long roomId, @Param("seatNo") String seatNo);

    @Select("SELECT COUNT(*) FROM seat WHERE room_id = #{roomId} AND seat_no = #{seatNo} AND id <> #{id}")
    long countByRoomAndSeatNoExcludeId(@Param("roomId") Long roomId,
                                       @Param("seatNo") String seatNo,
                                       @Param("id") Long id);

    @org.apache.ibatis.annotations.Insert("INSERT INTO seat(room_id, seat_no, x, y, has_power, near_window, status) " +
            "VALUES(#{roomId}, #{seatNo}, #{x}, #{y}, #{hasPower}, #{nearWindow}, #{status})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Seat seat);

    @Update("UPDATE seat SET room_id = #{roomId}, seat_no = #{seatNo}, x = #{x}, y = #{y}, " +
            "has_power = #{hasPower}, near_window = #{nearWindow}, status = #{status} WHERE id = #{id}")
    int update(Seat seat);

    @org.apache.ibatis.annotations.Delete("DELETE FROM seat WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

}
