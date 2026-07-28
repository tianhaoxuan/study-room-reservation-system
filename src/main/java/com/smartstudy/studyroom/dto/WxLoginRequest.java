package com.smartstudy.studyroom.dto;

import javax.validation.constraints.NotBlank;

public class WxLoginRequest {

    @NotBlank(message = "微信登录 code 不能为空")
    private String code;

    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    private String nickname;
    private String avatarUrl;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
