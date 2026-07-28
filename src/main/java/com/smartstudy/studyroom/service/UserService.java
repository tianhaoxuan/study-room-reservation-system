package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.UserInfoResponse;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 功能：获取当前用户信息。
     * 请求参数：userId 当前登录用户ID。
     * 返回值：学号、姓名、昵称、信用分、违规次数、账号状态。
     * 核心逻辑说明：根据 token 解析出的 userId 查询 user 表，不存在则返回未登录异常。
     */
    public UserInfoResponse getUserInfo(Long userId) {
        User user = requireUser(userId);
        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(user.getId());
        response.setStudentNo(user.getStudentNo());
        response.setRealName(user.getRealName());
        response.setNickname(user.getNickname());
        response.setCreditScore(user.getCreditScore());
        response.setViolationCount(user.getViolationCount());
        response.setStatus(user.getStatus());
        return response;
    }

    public User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(StatusCode.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        return user;
    }
}
