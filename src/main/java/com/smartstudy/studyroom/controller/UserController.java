package com.smartstudy.studyroom.controller;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.dto.UserInfoResponse;
import com.smartstudy.studyroom.service.TokenService;
import com.smartstudy.studyroom.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;

    public UserController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    /**
     * 功能：获取用户信息。
     * 请求参数：Authorization 请求头。
     * 返回值：统一 JSON，data 包含用户学号、姓名、昵称、信用分、违规次数和账号状态。
     * 核心逻辑说明：从 token 中解析 userId，再查询 user 表返回个人中心展示字段。
     */
    @GetMapping("/info")
    public ApiResponse<UserInfoResponse> info(@RequestHeader("Authorization") String authorization) {
        Long userId = tokenService.requireUserId(authorization);
        return ApiResponse.success(userService.getUserInfo(userId));
    }
}
