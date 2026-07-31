package com.smartstudy.studyroom.exception;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.common.StatusCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException e) {

        return fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {

        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "\u53c2\u6570\u9519\u8bef"
                : e.getBindingResult()
                        .getFieldErrors()
                        .get(0)
                        .getDefaultMessage();
        return fail(StatusCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException e) {

        String msg = e.getFieldErrors().isEmpty()
                ? "\u53c2\u6570\u9519\u8bef"
                : e.getFieldErrors().get(0).getDefaultMessage();
        return fail(StatusCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleParamException(
            Exception e) {

        return fail(
                StatusCode.PARAM_ERROR,
                "\u53c2\u6570\u9519\u8bef: " + e.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return fail(
                StatusCode.SERVER_ERROR,
                "\u670d\u52a1\u5668\u5f02\u5e38: " + e.getMessage()
        );
    }

    private ResponseEntity<ApiResponse<Void>> fail(
            int code,
            String message) {

        return ResponseEntity
                .status(toHttpStatus(code))
                .body(ApiResponse.fail(code, message));
    }

    private HttpStatus toHttpStatus(int code) {
        return switch (code) {
            case StatusCode.PARAM_ERROR -> HttpStatus.BAD_REQUEST;
            case StatusCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case StatusCode.FORBIDDEN -> HttpStatus.FORBIDDEN;
            case StatusCode.SERVER_ERROR ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
