package com.smartstudy.studyroom.controller.api;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.service.AdminSeatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seat")
public class ApiSeatController {

    private final AdminSeatService seatService;

    public ApiSeatController(AdminSeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/map")
    public ApiResponse<List<Seat>> map(@RequestParam Long roomId,
                                       @RequestParam String reservationDate,
                                       @RequestParam String timeSlot) {
        return ApiResponse.success(seatService.map(roomId, reservationDate, timeSlot));
    }
}
