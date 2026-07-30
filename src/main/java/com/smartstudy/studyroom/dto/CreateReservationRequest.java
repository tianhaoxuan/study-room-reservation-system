package com.smartstudy.studyroom.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class CreateReservationRequest {

    @NotNull(message = "座位ID不能为空")
    private Long seatId;

    @NotNull(message = "自习室ID不能为空")
    private Long roomId;

    @NotNull(message = "预约日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reservationDate;

    @NotNull(message = "开始时段ID不能为空")
    private Long startSlotId;

    @NotNull(message = "结束时段ID不能为空")
    private Long endSlotId;

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDate reservationDate) {
        this.reservationDate = reservationDate;
    }

    public Long getStartSlotId() {
        return startSlotId;
    }

    public void setStartSlotId(Long startSlotId) {
        this.startSlotId = startSlotId;
    }

    public Long getEndSlotId() {
        return endSlotId;
    }

    public void setEndSlotId(Long endSlotId) {
        this.endSlotId = endSlotId;
    }
}