package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.dto.LoginResponse;
import com.smartstudy.studyroom.dto.WxLoginRequest;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final TokenService tokenService;

    public AuthService(UserMapper userMapper, TokenService tokenService) {
        this.userMapper = userMapper;
        this.tokenService = tokenService;
    }

    /**
     * 功能：微信登录或自动注册。
     * 请求参数：code、studentNo、realName、nickname、avatarUrl。
     * 返回值：token、用户ID、学号、姓名、账号状态。
     * 核心逻辑说明：基础版用 code 模拟换取 openid；已有用户更新资料，新用户插入 user 表。
     */
    @Transactional
    public LoginResponse login(WxLoginRequest request) {
        String openid = mockExchangeOpenid(request.getCode());
        User user = userMapper.findByOpenid(openid);
        if (user == null) {
            user = userMapper.findByStudentNo(request.getStudentNo());
        }
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setStudentNo(request.getStudentNo());
            user.setRealName(request.getRealName());
            user.setNickname(request.getNickname());
            user.setAvatarUrl(request.getAvatarUrl());
            userMapper.insert(user);
        } else {
            user.setOpenid(openid);
            user.setStudentNo(request.getStudentNo());
            user.setRealName(request.getRealName());
            user.setNickname(request.getNickname());
            user.setAvatarUrl(request.getAvatarUrl());
            userMapper.updateLoginInfo(user);
            user = userMapper.findById(user.getId());
        }
        return new LoginResponse(tokenService.createToken(user.getId()), user.getId(),
                user.getStudentNo(), user.getRealName(), user.getStatus());
    }

    private String mockExchangeOpenid(String code) {
        if (code.startsWith("openid:")) {
            return code.substring("openid:".length());
        }
        return "mock_openid_" + code;
    }
}
