package com.smartstudy.studyroom.controller.api;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.entity.Notice;
import com.smartstudy.studyroom.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notice")
public class ApiNoticeController {

    private final NoticeService noticeService;

    public ApiNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/list")
    public ApiResponse<List<Notice>> list() {
        return ApiResponse.success(noticeService.list());
    }
}
