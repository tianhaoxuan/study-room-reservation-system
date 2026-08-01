package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.StudyRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface StudyRoomMapper {

    @Select("SELECT * FROM study_room WHERE id = #{id}")
    StudyRoom findById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT sr.*, b.building_name " +
            "FROM study_room sr LEFT JOIN building b ON sr.building_id = b.id " +
            "WHERE 1 = 1 " +
            "<if test='buildingId != null'> AND sr.building_id = #{buildingId}</if> " +
            "ORDER BY sr.id ASC" +
            "</script>")
    List<StudyRoom> findAll(@Param("buildingId") Long buildingId);

    @Select("SELECT id FROM study_room WHERE status = 1 ORDER BY id ASC")
    List<Long> findActiveRoomIds();

    @Select("SELECT COUNT(*) FROM study_room WHERE building_id = #{buildingId} AND room_name = #{roomName}")
    long countByBuildingAndName(@Param("buildingId") Long buildingId, @Param("roomName") String roomName);

    @Select("SELECT COUNT(*) FROM study_room WHERE building_id = #{buildingId} AND room_name = #{roomName} AND id <> #{id}")
    long countByBuildingAndNameExcludeId(@Param("buildingId") Long buildingId,
                                         @Param("roomName") String roomName,
                                         @Param("id") Long id);

    @org.apache.ibatis.annotations.Insert("INSERT INTO study_room(building_id, room_name, total_seats, reserved_seats, occupancy_rate, open_time, close_time, status) " +
            "VALUES(#{buildingId}, #{roomName}, #{totalSeats}, #{reservedSeats}, #{occupancyRate}, #{openTime}, #{closeTime}, #{status})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StudyRoom room);

    @Update("UPDATE study_room SET building_id = #{buildingId}, room_name = #{roomName}, total_seats = #{totalSeats}, " +
            "open_time = #{openTime}, close_time = #{closeTime}, status = #{status} WHERE id = #{id}")
    int update(StudyRoom room);

    @org.apache.ibatis.annotations.Delete("DELETE FROM study_room WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Update("UPDATE study_room SET reserved_seats = #{reservedSeats}, occupancy_rate = #{occupancyRate} WHERE id = #{roomId}")
    int updateSeatStats(@Param("roomId") Long roomId,
                        @Param("reservedSeats") Integer reservedSeats,
                        @Param("occupancyRate") BigDecimal occupancyRate);
}