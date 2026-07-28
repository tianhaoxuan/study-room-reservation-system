package com.smartstudy.studyroom.entity;

import java.time.LocalDateTime;

public class Seat {

    private Long id;
    private Long roomId;
    private String seatNo;
    private Integer x;
    private Integer y;
    private Integer hasPower;
    private Integer nearWindow;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(String seatNo) {
        this.seatNo = seatNo;
    }

    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }

    public Integer getY() {
        return y;
    }

    public void setY(Integer y) {
        this.y = y;
    }

    public Integer getHasPower() {
        return hasPower;
    }

    public void setHasPower(Integer hasPower) {
        this.hasPower = hasPower;
    }

    public Integer getNearWindow() {
        return nearWindow;
    }

    public void setNearWindow(Integer nearWindow) {
        this.nearWindow = nearWindow;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
