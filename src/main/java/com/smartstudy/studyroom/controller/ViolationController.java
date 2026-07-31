package com.smartstudy.studyroom.controller;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.dto.ViolationResponse;
import com.smartstudy.studyroom.service.TokenService;
import com.smartstudy.studyroom.service.ViolationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/violation")
public class ViolationController {

    private final ViolationService violationService;
    private final TokenService tokenService;

    public ViolationController(ViolationService violationService, TokenService tokenService) {
        this.violationService = violationService;
        this.tokenService = tokenService;
    }

    /**
     * 功能：查询我的违规记录。
     * 请求参数：Authorization 请求头。
     * 返回值：统一 JSON，data 为违规记录列表。
     * 核心逻辑说明：根据 token 获取当前用户ID，只返回本人 violation 表记录。
     */
    @GetMapping("/my")
    public ApiResponse<List<ViolationResponse>> my(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Long userId = tokenService.requireUserId(authorization);
        return ApiResponse.success(violationService.findMyViolations(userId));
    }
}
