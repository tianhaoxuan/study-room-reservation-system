package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.dto.ViolationResponse;
import com.smartstudy.studyroom.mapper.ViolationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ViolationService {

    private final ViolationMapper violationMapper;

    public ViolationService(ViolationMapper violationMapper) {
        this.violationMapper = violationMapper;
    }

    /**
     * 功能：查询我的违规记录。
     * 请求参数：userId 当前登录用户ID。
     * 返回值：违规记录列表。
     * 核心逻辑说明：按当前用户ID查询 violation 表，按创建时间倒序返回。
     */
    public List<ViolationResponse> findMyViolations(Long userId) {
        return violationMapper.findByUserId(userId);
    }
}
