package com.smartstudy.studyroom.controller;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.common.PageResult;
import com.smartstudy.studyroom.dto.CancelReservationRequest;
import com.smartstudy.studyroom.dto.CreateReservationRequest;
import com.smartstudy.studyroom.dto.CreateReservationResponse;
import com.smartstudy.studyroom.dto.MyReservationResponse;
import com.smartstudy.studyroom.service.ReservationService;
import com.smartstudy.studyroom.service.TokenService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/reservation")
public class ReservationController {

    private final ReservationService reservationService;
    private final TokenService tokenService;

    public ReservationController(ReservationService reservationService, TokenService tokenService) {
        this.reservationService = reservationService;
        this.tokenService = tokenService;
    }

    /**
     * 功能：创建预约。
     * 请求参数：Authorization 请求头；body 包含 seatId、roomId、reservationDate、timeSlot、startTime、endTime。
     * 返回值：统一 JSON，data 包含 reservationId 和 status。
     * 核心逻辑说明：业务层完成账号状态、座位维修、座位冲突、用户冲突和每日限约校验。
     */
    @PostMapping("/create")
    public ApiResponse<CreateReservationResponse> create(@RequestHeader("Authorization") String authorization,
                                                         @Valid @RequestBody CreateReservationRequest request) {
        Long userId = tokenService.requireUserId(authorization);
        return ApiResponse.success("预约成功", reservationService.createReservation(userId, request));
    }

    /**
     * 功能：取消预约。
     * 请求参数：Authorization 请求头；body 包含 reservationId。
     * 返回值：统一 JSON，data 为 null。
     * 核心逻辑说明：只能取消本人待签到预约，取消成功后释放座位并刷新房间统计。
     */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestHeader("Authorization") String authorization,
                                    @Valid @RequestBody CancelReservationRequest request) {
        Long userId = tokenService.requireUserId(authorization);
        reservationService.cancelReservation(userId, request.getReservationId());
        return ApiResponse.success("取消成功", null);
    }

    /**
     * 功能：我的预约查询。
     * 请求参数：Authorization 请求头；可选 status、pageNum、pageSize。
     * 返回值：统一 JSON，data 包含 total 和 records。
     * 核心逻辑说明：按当前用户分页查询预约记录，可按预约状态筛选。
     */
    @GetMapping("/my")
    public ApiResponse<PageResult<MyReservationResponse>> my(@RequestHeader("Authorization") String authorization,
                                                             @RequestParam(value = "status", required = false) Integer status,
                                                             @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                                             @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long userId = tokenService.requireUserId(authorization);
        return ApiResponse.success(reservationService.findMyReservations(userId, status, pageNum, pageSize));
    }
}
