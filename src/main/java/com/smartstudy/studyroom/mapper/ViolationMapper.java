package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.dto.ViolationResponse;
import com.smartstudy.studyroom.entity.Violation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ViolationMapper {

    @Insert("INSERT INTO violation(user_id, reservation_id, violation_type, reason, handle_result) " +
            "VALUES(#{userId}, #{reservationId}, #{violationType}, #{reason}, #{handleResult})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Violation violation);

    @Select("SELECT id AS violation_id, reservation_id, violation_type, reason, handle_result, create_time " +
            "FROM violation WHERE user_id = #{userId} ORDER BY create_time DESC, id DESC")
    List<ViolationResponse> findByUserId(@Param("userId") Long userId);
}
