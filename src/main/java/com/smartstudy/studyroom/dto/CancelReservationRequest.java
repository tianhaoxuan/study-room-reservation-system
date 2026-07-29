package com.smartstudy.studyroom.dto;

import jakarta.validation.constraints.NotNull;

public class CancelReservationRequest {

    @NotNull(message = "预约ID不能为空")
    private Long reservationId;

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }
}
