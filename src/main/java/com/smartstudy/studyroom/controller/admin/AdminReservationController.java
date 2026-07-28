package com.smartstudy.studyroom.controller.admin;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.common.PageResult;
import com.smartstudy.studyroom.dto.AdminCancelReservationRequest;
import com.smartstudy.studyroom.dto.AdminReservationResponse;
import com.smartstudy.studyroom.service.AdminReservationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservation")
public class AdminReservationController {

    private final AdminReservationService reservationService;

    public AdminReservationController(AdminReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<AdminReservationResponse>> list(
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String reservationDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(reservationService.list(studentNo, roomId, status,
                reservationDate, pageNum, pageSize));
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody AdminCancelReservationRequest request) {
        reservationService.cancel(request.getReservationId(), request.getReason());
        return ApiResponse.success("取消成功", null);
    }
}
