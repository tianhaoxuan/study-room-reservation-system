package com.smartstudy.studyroom.dto;

import jakarta.validation.constraints.NotNull;

public class AdminCancelReservationRequest {

    @NotNull(message = "预约ID不能为空")
    private Long reservationId;

    private String reason;

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
