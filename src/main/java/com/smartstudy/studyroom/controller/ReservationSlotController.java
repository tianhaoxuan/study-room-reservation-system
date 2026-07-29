package com.smartstudy.studyroom.controller;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.dto.ReservationSlotResponse;
import com.smartstudy.studyroom.service.ReservationSlotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reservation/slots")
public class ReservationSlotController {

    private final ReservationSlotService reservationSlotService;

    public ReservationSlotController(
            ReservationSlotService reservationSlotService) {
        this.reservationSlotService = reservationSlotService;
    }

    @GetMapping
    public ApiResponse<List<ReservationSlotResponse>> list(
            @RequestParam Long roomId) {
        return ApiResponse.success(
                reservationSlotService.findSelectableSlots(roomId)
        );
    }
}