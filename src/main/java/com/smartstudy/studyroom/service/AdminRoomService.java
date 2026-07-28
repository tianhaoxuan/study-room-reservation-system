package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.BuildingMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminRoomService {

    private final StudyRoomMapper studyRoomMapper;
    private final BuildingMapper buildingMapper;

    public AdminRoomService(StudyRoomMapper studyRoomMapper, BuildingMapper buildingMapper) {
        this.studyRoomMapper = studyRoomMapper;
        this.buildingMapper = buildingMapper;
    }

    public List<StudyRoom> list(Long buildingId) {
        return studyRoomMapper.findAll(buildingId);
    }

    @Transactional
    public void add(StudyRoom room) {
        validateRoomForSave(room, true);
        if (studyRoomMapper.countByBuildingAndName(room.getBuildingId(), room.getRoomName()) > 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "该楼栋下已存在相同名称的自习室");
        }
        room.setReservedSeats(0);
        room.setOccupancyRate(BigDecimal.ZERO);
        if (room.getStatus() == null) {
            room.setStatus(1);
        }
        studyRoomMapper.insert(room);
    }

    @Transactional
    public void update(StudyRoom room) {
        validateRoomForSave(room, false);
        if (studyRoomMapper.findById(room.getId()) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "自习室不存在");
        }
        if (studyRoomMapper.countByBuildingAndNameExcludeId(room.getBuildingId(), room.getRoomName(), room.getId()) > 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "该楼栋下已存在相同名称的自习室");
        }
        if (room.getStatus() == null) {
            room.setStatus(1);
        }
        studyRoomMapper.update(room);
    }

    @Transactional
    public void delete(Long id) {
        if (studyRoomMapper.findById(id) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "自习室不存在");
        }
        studyRoomMapper.deleteById(id);
    }

    private void validateRoomForSave(StudyRoom room, boolean add) {
        if (room == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "自习室参数不能为空");
        }
        if (!add && room.getId() == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "自习室ID不能为空");
        }
        if (room.getBuildingId() == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋ID不能为空");
        }
        if (buildingMapper.findById(room.getBuildingId()) == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "楼栋不存在");
        }
        if (!StringUtils.hasText(room.getRoomName())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "自习室名称不能为空");
        }
        if (room.getTotalSeats() == null || room.getTotalSeats() <= 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "总座位数必须大于0");
        }
        if (room.getOpenTime() == null || room.getCloseTime() == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "开放时间和关闭时间不能为空");
        }
        if (!room.getCloseTime().isAfter(room.getOpenTime())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "关闭时间必须晚于开放时间");
        }
    }
}
