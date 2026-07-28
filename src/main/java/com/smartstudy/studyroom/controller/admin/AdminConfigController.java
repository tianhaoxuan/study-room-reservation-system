package com.smartstudy.studyroom.controller.admin;

import com.smartstudy.studyroom.common.ApiResponse;
import com.smartstudy.studyroom.service.AdminConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    private final AdminConfigService configService;

    public AdminConfigController(AdminConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list() {
        return ApiResponse.success(configService.list());
    }

    @PutMapping("/update")
    public ApiResponse<Void> update(@RequestBody Map<String, Object> configs) {
        configService.update(configs);
        return ApiResponse.success("修改成功", null);
    }
}
