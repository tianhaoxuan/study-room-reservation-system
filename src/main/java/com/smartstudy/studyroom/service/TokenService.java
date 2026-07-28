package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TokenService {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String USER_TOKEN_PREFIX = "user-";

    public String createToken(Long userId) {
        return USER_TOKEN_PREFIX + userId;
    }

    /**
     * 功能：解析登录请求头中的用户ID。
     * 请求参数：Authorization: Bearer user-{userId}。
     * 返回值：当前登录用户ID。
     * 核心逻辑说明：基础版先使用可测试的简易 token，后续可替换为 JWT 解析。
     */
    public Long requireUserId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(TOKEN_PREFIX)) {
            throw new BusinessException(StatusCode.UNAUTHORIZED, "未登录或 token 缺失");
        }
        String token = authorization.substring(TOKEN_PREFIX.length()).trim();
        if (!token.startsWith(USER_TOKEN_PREFIX)) {
            throw new BusinessException(StatusCode.UNAUTHORIZED, "token 格式错误");
        }
        try {
            return Long.valueOf(token.substring(USER_TOKEN_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(StatusCode.UNAUTHORIZED, "token 无效");
        }
    }
}
