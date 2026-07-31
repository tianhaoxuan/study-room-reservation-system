package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.common.UserRole;
import com.smartstudy.studyroom.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

@Service
public class TokenService {

    private static final String AUTHORIZATION_PREFIX = "Bearer ";
    private static final String TOKEN_PREFIX = "st.";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String tokenSecret;
    private final long tokenTtlSeconds;
    private final Clock clock;

    @Autowired
    public TokenService(
            @Value("${studyroom.auth.token-secret}") String tokenSecret,
            @Value("${studyroom.auth.token-ttl-seconds}") long tokenTtlSeconds) {

        this(tokenSecret, tokenTtlSeconds, Clock.systemUTC());
    }

    public TokenService(
            String tokenSecret,
            long tokenTtlSeconds,
            Clock clock) {

        if (!StringUtils.hasText(tokenSecret)) {
            throw new IllegalArgumentException("tokenSecret must not be blank");
        }
        if (tokenTtlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "tokenTtlSeconds must be positive"
            );
        }
        this.tokenSecret = tokenSecret;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.clock = clock;
    }

    public String createToken(Long userId) {
        return createToken(userId, UserRole.USER.name());
    }

    public String createToken(Long userId, String role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        UserRole userRole = UserRole.from(role);
        long expiresAt = Instant.now(clock)
                .plusSeconds(tokenTtlSeconds)
                .getEpochSecond();
        String payload = userId + ":" + userRole.name() + ":" + expiresAt;
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedPayload);
        return TOKEN_PREFIX + encodedPayload + "." + signature;
    }

    public Long requireUserId(String authorization) {
        return requireUser(authorization).userId();
    }

    public AuthenticatedUser requireUser(String authorization) {
        String token = extractBearerToken(authorization);
        return parseToken(token);
    }

    public AuthenticatedUser requireAdmin(String authorization) {
        AuthenticatedUser user = requireUser(authorization);
        if (!user.isAdmin()) {
            throw new BusinessException(
                    StatusCode.FORBIDDEN,
                    "\u9700\u8981\u7ba1\u7406\u5458\u6743\u9650"
            );
        }
        return user;
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith(AUTHORIZATION_PREFIX)) {
            throw new BusinessException(
                    StatusCode.UNAUTHORIZED,
                    "\u672a\u767b\u5f55\u6216 token \u7f3a\u5931"
            );
        }
        String token = authorization.substring(AUTHORIZATION_PREFIX.length())
                .trim();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(
                    StatusCode.UNAUTHORIZED,
                    "\u672a\u767b\u5f55\u6216 token \u7f3a\u5931"
            );
        }
        return token;
    }

    private AuthenticatedUser parseToken(String token) {
        if (!token.startsWith(TOKEN_PREFIX)) {
            throw unauthorized("token \u683c\u5f0f\u9519\u8bef");
        }

        String rawToken = token.substring(TOKEN_PREFIX.length());
        String[] parts = rawToken.split("\\.", -1);
        if (parts.length != 2
                || !StringUtils.hasText(parts[0])
                || !StringUtils.hasText(parts[1])) {
            throw unauthorized("token \u683c\u5f0f\u9519\u8bef");
        }

        String expectedSignature = sign(parts[0]);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw unauthorized("token \u7b7e\u540d\u65e0\u6548");
        }

        String payload = decodePayload(parts[0]);
        String[] payloadParts = payload.split(":", -1);
        if (payloadParts.length != 3) {
            throw unauthorized("token \u65e0\u6548");
        }

        try {
            Long userId = Long.valueOf(payloadParts[0]);
            UserRole role = UserRole.from(payloadParts[1]);
            long expiresAt = Long.parseLong(payloadParts[2]);
            if (expiresAt < Instant.now(clock).getEpochSecond()) {
                throw unauthorized("token \u5df2\u8fc7\u671f");
            }
            return new AuthenticatedUser(userId, role);
        } catch (IllegalArgumentException e) {
            throw unauthorized("token \u65e0\u6548");
        }
    }

    private String decodePayload(String encodedPayload) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encodedPayload);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw unauthorized("token \u65e0\u6548");
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    tokenSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            ));
            return encode(mac.doFinal(
                    encodedPayload.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign token", e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private BusinessException unauthorized(String message) {
        return new BusinessException(StatusCode.UNAUTHORIZED, message);
    }
}
