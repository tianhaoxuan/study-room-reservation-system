package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.Notice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NoticeMapper {

    @Select("SELECT * FROM notice ORDER BY create_time DESC, id DESC")
    List<Notice> findAll();
}
