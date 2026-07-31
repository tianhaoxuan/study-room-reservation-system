package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnBadRequestForParamBusinessException() throws Exception {
        mockMvc.perform(get("/test/errors/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(StatusCode.PARAM_ERROR))
                .andExpect(jsonPath("$.msg").value("bad request"));
    }

    @Test
    void shouldReturnUnauthorizedForUnauthorizedBusinessException()
            throws Exception {

        mockMvc.perform(get("/test/errors/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.msg").value("unauthorized"));
    }

    @Test
    void shouldReturnForbiddenForForbiddenBusinessException() throws Exception {
        mockMvc.perform(get("/test/errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.msg").value("forbidden"));
    }

    @Test
    void shouldReturnInternalServerErrorForUnexpectedException()
            throws Exception {

        mockMvc.perform(get("/test/errors/server"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(StatusCode.SERVER_ERROR))
                .andExpect(jsonPath("$.msg")
                        .value("\u670d\u52a1\u5668\u5f02\u5e38: boom"));
    }

    @Test
    void shouldReturnBadRequestForValidationException() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(StatusCode.PARAM_ERROR))
                .andExpect(jsonPath("$.msg").value("name is required"));
    }

    @RestController
    @RequestMapping("/test/errors")
    static class TestController {

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BusinessException(
                    StatusCode.PARAM_ERROR,
                    "bad request"
            );
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new BusinessException(
                    StatusCode.UNAUTHORIZED,
                    "unauthorized"
            );
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new BusinessException(StatusCode.FORBIDDEN, "forbidden");
        }

        @GetMapping("/server")
        void server() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody ValidationRequest request) {
        }
    }

    record ValidationRequest(
            @NotBlank(message = "name is required") String name) {
    }
}
