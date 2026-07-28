package com.smartstudy.studyroom.controller;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.dto.LoginResponse;
import com.smartstudy.studyroom.dto.WxLoginRequest;
import com.smartstudy.studyroom.service.AuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/wx")
public class WxLoginController {

    private final AuthService authService;

    public WxLoginController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 功能：微信登录。
     * 请求参数：code、studentNo、realName、nickname、avatarUrl。
     * 返回值：统一 JSON，data 包含 token、userId、studentNo、realName、status。
     * 核心逻辑说明：调用业务层模拟 code 换 openid，完成用户注册或资料更新。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody WxLoginRequest request) {
        return ApiResponse.success("登录成功", authService.login(request));
    }
}
