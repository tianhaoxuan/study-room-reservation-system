package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.common.UserRole;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.service.AuthenticatedUser;
import com.smartstudy.studyroom.service.TokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-31T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void shouldCreateAndParseSignedUserToken() {
        TokenService tokenService = new TokenService(
                "test-secret",
                3600,
                FIXED_CLOCK
        );

        String token = tokenService.createToken(1001L, UserRole.USER.name());

        AuthenticatedUser user =
                tokenService.requireUser("Bearer " + token);

        assertEquals(1001L, user.userId());
        assertEquals(UserRole.USER, user.role());
    }

    @Test
    void shouldRejectTamperedToken() {
        TokenService tokenService = new TokenService(
                "test-secret",
                3600,
                FIXED_CLOCK
        );
        String token = tokenService.createToken(1001L, UserRole.USER.name());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tokenService.requireUser("Bearer " + token + "x")
        );

        assertEquals(StatusCode.UNAUTHORIZED, exception.getCode());
    }

    @Test
    void shouldRejectExpiredToken() {
        TokenService issuer = new TokenService(
                "test-secret",
                1,
                FIXED_CLOCK
        );
        TokenService verifier = new TokenService(
                "test-secret",
                1,
                Clock.fixed(
                        Instant.parse("2026-07-31T00:00:02Z"),
                        ZoneOffset.UTC
                )
        );
        String token = issuer.createToken(1001L, UserRole.USER.name());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> verifier.requireUser("Bearer " + token)
        );

        assertEquals(StatusCode.UNAUTHORIZED, exception.getCode());
    }

    @Test
    void shouldRejectNormalUserForAdminApi() {
        TokenService tokenService = new TokenService(
                "test-secret",
                3600,
                FIXED_CLOCK
        );
        String token = tokenService.createToken(1001L, UserRole.USER.name());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tokenService.requireAdmin("Bearer " + token)
        );

        assertEquals(StatusCode.FORBIDDEN, exception.getCode());
    }
}
