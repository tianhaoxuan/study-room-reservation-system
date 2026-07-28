package com.smartstudy.studyroom.dto;

public class StatusResponse {

    private Long reservationId;
    private Integer status;

    public StatusResponse() {
    }

    public StatusResponse(Long reservationId, Integer status) {
        this.reservationId = reservationId;
        this.status = status;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
