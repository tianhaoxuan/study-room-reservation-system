package com.smartstudy.studyroom.dto;

public class LoginResponse {

    private String token;
    private Long userId;
    private String studentNo;
    private String realName;
    private Integer status;
    private String role;

    public LoginResponse() {
    }

    public LoginResponse(
            String token,
            Long userId,
            String studentNo,
            String realName,
            Integer status,
            String role) {

        this.token = token;
        this.userId = userId;
        this.studentNo = studentNo;
        this.realName = realName;
        this.status = status;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
