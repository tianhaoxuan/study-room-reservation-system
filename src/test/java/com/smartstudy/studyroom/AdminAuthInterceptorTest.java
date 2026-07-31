package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.common.UserRole;
import com.smartstudy.studyroom.config.AdminAuthInterceptor;
import com.smartstudy.studyroom.exception.GlobalExceptionHandler;
import com.smartstudy.studyroom.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuthInterceptorTest {

    private TokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(
                "test-secret",
                3600,
                Clock.fixed(
                        Instant.parse("2026-07-31T00:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestAdminController())
                .addInterceptors(new AdminAuthInterceptor(tokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRejectAdminApiWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    void shouldRejectAdminApiWithNormalUserToken() throws Exception {
        String token = tokenService.createToken(1001L, UserRole.USER.name());

        mockMvc.perform(get("/api/admin/test/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    @Test
    void shouldAllowAdminApiWithAdminToken() throws Exception {
        String token = tokenService.createToken(1L, UserRole.ADMIN.name());

        mockMvc.perform(get("/api/admin/test/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @RestController
    @RequestMapping("/api/admin/test")
    static class TestAdminController {

        @GetMapping("/ping")
        ApiResponse<String> ping() {
            return ApiResponse.success("pong");
        }
    }
}
