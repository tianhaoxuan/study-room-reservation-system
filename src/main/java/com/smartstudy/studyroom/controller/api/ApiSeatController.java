package com.smartstudy.studyroom.controller.api;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.dto.SeatAvailabilityResponse;
import com.smartstudy.studyroom.service.SeatAvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/seat")
public class ApiSeatController {

    private final SeatAvailabilityService seatAvailabilityService;

    public ApiSeatController(
            SeatAvailabilityService seatAvailabilityService) {
        this.seatAvailabilityService = seatAvailabilityService;
    }

    @GetMapping("/map")
    public ApiResponse<List<SeatAvailabilityResponse>> map(
            @RequestParam Long roomId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reservationDate,
            @RequestParam Long startSlotId,
            @RequestParam Long endSlotId) {

        return ApiResponse.success(
                seatAvailabilityService.findAvailableSeats(
                        roomId,
                        reservationDate,
                        startSlotId,
                        endSlotId
                )
        );
    }
}