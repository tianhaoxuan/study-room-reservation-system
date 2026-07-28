package com.smartstudy.studyroom.controller.admin;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.service.AdminRoomService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/room")
public class AdminRoomController {

    private final AdminRoomService roomService;

    public AdminRoomController(AdminRoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/list")
    public ApiResponse<List<StudyRoom>> list(@RequestParam(required = false) Long buildingId) {
        return ApiResponse.success(roomService.list(buildingId));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody StudyRoom room) {
        roomService.add(room);
        return ApiResponse.success("新增成功", null);
    }

    @PutMapping("/update")
    public ApiResponse<Void> update(@RequestBody StudyRoom room) {
        roomService.update(room);
        return ApiResponse.success("修改成功", null);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
