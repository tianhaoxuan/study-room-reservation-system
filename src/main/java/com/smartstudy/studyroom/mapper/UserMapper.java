package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM `user` WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT * FROM `user` WHERE openid = #{openid}")
    User findByOpenid(@Param("openid") String openid);

    @Select("SELECT * FROM `user` WHERE student_no = #{studentNo}")
    User findByStudentNo(@Param("studentNo") String studentNo);

    @Insert("INSERT INTO `user`(openid, student_no, real_name, nickname, avatar_url, credit_score, violation_count, status, role) " +
            "VALUES(#{openid}, #{studentNo}, #{realName}, #{nickname}, #{avatarUrl}, 100, 0, 1, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE `user` SET openid = #{openid}, student_no = #{studentNo}, real_name = #{realName}, nickname = #{nickname}, " +
            "avatar_url = #{avatarUrl} WHERE id = #{id}")
    int updateLoginInfo(User user);

    @Update("UPDATE `user` SET violation_count = violation_count + 1, " +
            "credit_score = CASE WHEN credit_score >= 10 THEN credit_score - 10 ELSE 0 END WHERE id = #{userId}")
    int increaseViolation(@Param("userId") Long userId);

    @Update("UPDATE `user` SET status = 0 WHERE id = #{userId}")
    int banUser(@Param("userId") Long userId);
}
