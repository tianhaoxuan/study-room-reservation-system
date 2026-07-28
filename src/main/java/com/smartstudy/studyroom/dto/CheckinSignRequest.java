package com.smartstudy.studyroom.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class CheckinSignRequest {

    @NotNull(message = "预约ID不能为空")
    private Long reservationId;

    @NotBlank(message = "座位二维码不能为空")
    private String seatCode;

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public String getSeatCode() {
        return seatCode;
    }

    public void setSeatCode(String seatCode) {
        this.seatCode = seatCode;
    }
}
