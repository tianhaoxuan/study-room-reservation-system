package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SystemConfigMapper {

    @Select("SELECT config_value FROM system_config WHERE config_key = #{configKey}")
    String findValue(@Param("configKey") String configKey);

    @Select("SELECT * FROM system_config WHERE config_key IN ('checkin_limit_minutes', 'max_reservation_per_day', 'violation_limit')")
    List<SystemConfig> findCoreConfigs();

    @Select("SELECT * FROM system_config WHERE config_key = #{configKey}")
    SystemConfig findByKey(@Param("configKey") String configKey);

    @Update("UPDATE system_config SET config_value = #{configValue}, description = #{description} WHERE config_key = #{configKey}")
    int updateByKey(SystemConfig config);

    @Insert("INSERT INTO system_config(config_key, config_value, description) VALUES(#{configKey}, #{configValue}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SystemConfig config);
}
