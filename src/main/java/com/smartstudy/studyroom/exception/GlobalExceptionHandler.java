package com.smartstudy.studyroom.exception;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.common.StatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数错误"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ApiResponse.fail(StatusCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().isEmpty() ? "参数错误" : e.getFieldErrors().get(0).getDefaultMessage();
        return ApiResponse.fail(StatusCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleParamException(Exception e) {
        return ApiResponse.fail(StatusCode.PARAM_ERROR, "参数错误：" + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        return ApiResponse.fail(StatusCode.SERVER_ERROR, "服务器异常：" + e.getMessage());
    }
}
