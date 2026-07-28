package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.Building;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BuildingMapper {

    @Select("SELECT * FROM building ORDER BY id ASC")
    List<Building> findAll();

    @Select("SELECT * FROM building WHERE id = #{id}")
    Building findById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM building WHERE building_name = #{buildingName}")
    long countByName(@Param("buildingName") String buildingName);

    @Select("SELECT COUNT(*) FROM building WHERE building_name = #{buildingName} AND id <> #{id}")
    long countByNameExcludeId(@Param("buildingName") String buildingName, @Param("id") Long id);

    @Insert("INSERT INTO building(building_name, status) VALUES(#{buildingName}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Building building);

    @Update("UPDATE building SET building_name = #{buildingName}, status = #{status} WHERE id = #{id}")
    int update(Building building);

    @Delete("DELETE FROM building WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
