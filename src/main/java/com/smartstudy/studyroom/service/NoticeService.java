package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.entity.Notice;
import com.smartstudy.studyroom.mapper.NoticeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoticeService {

    private final NoticeMapper noticeMapper;

    public NoticeService(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    public List<Notice> list() {
        return noticeMapper.findAll();
    }
}
