package com.smartstudy.studyroom.dto;

public record SeatAvailabilityResponse(
        Long id,
        Long roomId,
        String seatNo,
        Integer x,
        Integer y,
        Integer hasPower,
        Integer nearWindow,
        Integer status
) {
}