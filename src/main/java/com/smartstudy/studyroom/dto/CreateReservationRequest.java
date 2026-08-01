package com.smartstudy.studyroom.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CreateReservationRequest {

    @NotBlank(message = "requestId must not be blank")
    @Size(max = 64, message = "requestId length must not exceed 64")
    private String requestId;

    @NotNull(message = "搴т綅ID涓嶈兘涓虹┖")
    private Long seatId;

    @NotNull(message = "鑷範瀹D涓嶈兘涓虹┖")
    private Long roomId;

    @NotNull(message = "棰勭害鏃ユ湡涓嶈兘涓虹┖")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reservationDate;

    @NotNull(message = "寮€濮嬫椂娈礗D涓嶈兘涓虹┖")
    private Long startSlotId;

    @NotNull(message = "缁撴潫鏃舵ID涓嶈兘涓虹┖")
    private Long endSlotId;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

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