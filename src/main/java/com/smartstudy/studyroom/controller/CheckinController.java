package com.smartstudy.studyroom.controller;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.dto.CancelReservationRequest;
import com.smartstudy.studyroom.dto.CheckinSignRequest;
import com.smartstudy.studyroom.dto.StatusResponse;
import com.smartstudy.studyroom.service.CheckinService;
import com.smartstudy.studyroom.service.TokenService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    private final CheckinService checkinService;
    private final TokenService tokenService;

    public CheckinController(CheckinService checkinService, TokenService tokenService) {
        this.checkinService = checkinService;
        this.tokenService = tokenService;
    }

    /**
     * 功能：扫码签到。
     * 请求参数：Authorization 请求头；body 包含 reservationId、seatCode。
     * 返回值：统一 JSON，data 包含 reservationId 和 status。
     * 核心逻辑说明：校验本人预约、待签到状态、二维码座位一致性和签到宽容时间。
     */
    @PostMapping("/sign")
    public ApiResponse<StatusResponse> sign(@RequestHeader("Authorization") String authorization,
                                            @Valid @RequestBody CheckinSignRequest request) {
        Long userId = tokenService.requireUserId(authorization);
        return ApiResponse.success("签到成功",
                checkinService.sign(userId, request.getReservationId(), request.getSeatCode()));
    }

    /**
     * 功能：提前退座。
     * 请求参数：Authorization 请求头；body 包含 reservationId。
     * 返回值：统一 JSON，data 为 null。
     * 核心逻辑说明：只能退本人使用中的预约，成功后将预约置为已完成并释放座位。
     */
    @PostMapping("/leave")
    public ApiResponse<Void> leave(@RequestHeader("Authorization") String authorization,
                                   @Valid @RequestBody CancelReservationRequest request) {
        Long userId = tokenService.requireUserId(authorization);
        checkinService.leave(userId, request.getReservationId());
        return ApiResponse.success("退座成功", null);
    }
}
