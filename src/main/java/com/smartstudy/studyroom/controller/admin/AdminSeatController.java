package com.smartstudy.studyroom.controller.admin;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.dto.SeatImportResult;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.service.AdminSeatService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/seat")
public class AdminSeatController {

    private final AdminSeatService seatService;

    public AdminSeatController(AdminSeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/list")
    public ApiResponse<List<Seat>> list(@RequestParam Long roomId) {
        return ApiResponse.success(seatService.list(roomId));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody Seat seat) {
        seatService.add(seat);
        return ApiResponse.success("新增成功", null);
    }

    @PutMapping("/update")
    public ApiResponse<Void> update(@RequestBody Seat seat) {
        seatService.update(seat);
        return ApiResponse.success("修改成功", null);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        seatService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/import")
    public ApiResponse<SeatImportResult> importSeats(@RequestParam("file") MultipartFile file,
                                                     @RequestParam Long roomId) {
        return ApiResponse.success("导入成功", seatService.importSeats(file, roomId));
    }
}
