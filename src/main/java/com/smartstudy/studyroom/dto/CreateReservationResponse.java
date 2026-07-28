package com.smartstudy.studyroom.dto;

public class CreateReservationResponse {

    private Long reservationId;
    private Integer status;

    public CreateReservationResponse() {
    }

    public CreateReservationResponse(Long reservationId, Integer status) {
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
