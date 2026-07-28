package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.entity.Building;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.BuildingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AdminBuildingService {

    private final BuildingMapper buildingMapper;

    public AdminBuildingService(BuildingMapper buildingMapper) {
        this.buildingMapper = buildingMapper;
    }

    public List<Building> list() {
        return buildingMapper.findAll();
    }

    @Transactional
    public void add(Building building) {
        if (building == null || !StringUtils.hasText(building.getBuildingName())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋名称不能为空");
        }
        if (buildingMapper.countByName(building.getBuildingName()) > 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋名称已存在");
        }
        if (building.getStatus() == null) {
            building.setStatus(1);
        }
        buildingMapper.insert(building);
    }

    @Transactional
    public void update(Building building) {
        if (building == null || building.getId() == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋ID不能为空");
        }
        if (!StringUtils.hasText(building.getBuildingName())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋名称不能为空");
        }
        if (buildingMapper.findById(building.getId()) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋不存在");
        }
        if (buildingMapper.countByNameExcludeId(building.getBuildingName(), building.getId()) > 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋名称已存在");
        }
        if (building.getStatus() == null) {
            building.setStatus(1);
        }
        buildingMapper.update(building);
    }

    @Transactional
    public void delete(Long id) {
        if (buildingMapper.findById(id) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋不存在");
        }
        buildingMapper.deleteById(id);
    }
}
