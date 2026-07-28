package com.smartstudy.studyroom.controller.api;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.entity.Building;
import com.smartstudy.studyroom.service.AdminBuildingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/building")
public class ApiBuildingController {

    private final AdminBuildingService buildingService;

    public ApiBuildingController(AdminBuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @GetMapping("/list")
    public ApiResponse<List<Building>> list() {
        return ApiResponse.success(buildingService.list());
    }
}
