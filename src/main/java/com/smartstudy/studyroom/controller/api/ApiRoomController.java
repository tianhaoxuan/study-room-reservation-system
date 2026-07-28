package com.smartstudy.studyroom.controller.api;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.service.AdminRoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/room")
public class ApiRoomController {

    private final AdminRoomService roomService;

    public ApiRoomController(AdminRoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/list")
    public ApiResponse<List<StudyRoom>> list(@RequestParam(required = false) Long buildingId) {
        return ApiResponse.success(roomService.list(buildingId));
    }
}
