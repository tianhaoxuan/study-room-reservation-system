package com.smartstudy.studyroom.controller.admin;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.entity.Building;
import com.smartstudy.studyroom.service.AdminBuildingService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/building")
public class AdminBuildingController {

    private final AdminBuildingService buildingService;

    public AdminBuildingController(AdminBuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @GetMapping("/list")
    public ApiResponse<List<Building>> list() {
        return ApiResponse.success(buildingService.list());
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody Building building) {
        buildingService.add(building);
        return ApiResponse.success("新增成功", null);
    }

    @PutMapping("/update")
    public ApiResponse<Void> update(@RequestBody Building building) {
        buildingService.update(building);
        return ApiResponse.success("修改成功", null);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        buildingService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
