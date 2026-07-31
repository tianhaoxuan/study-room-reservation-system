package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.entity.Violation;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.ReservationSlotOccupancyMapper;
import com.smartstudy.studyroom.mapper.UserMapper;
import com.smartstudy.studyroom.mapper.ViolationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationTimeoutService {

    private final ReservationMapper reservationMapper;
    private final ReservationSlotOccupancyMapper reservationSlotOccupancyMapper;
    private final ViolationMapper violationMapper;
    private final UserMapper userMapper;
    private final ConfigService configService;
    private final RoomStatsService roomStatsService;

    public ReservationTimeoutService(ReservationMapper reservationMapper,
                                     ReservationSlotOccupancyMapper reservationSlotOccupancyMapper,
                                     ViolationMapper violationMapper,
                                     UserMapper userMapper,
                                     ConfigService configService,
                                     RoomStatsService roomStatsService) {
        this.reservationMapper = reservationMapper;
        this.reservationSlotOccupancyMapper = reservationSlotOccupancyMapper;
        this.violationMapper = violationMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.roomStatsService = roomStatsService;
    }

    /**
     * 功能：释放超时未签到预约。
     * 请求参数：无，后台定时任务触发。
     * 返回值：本次处理的超时预约数量。
     * 核心逻辑说明：查询待签到预约，超过开始时间+宽容分钟则标记违约、写违规、累加违规次数、达到阈值封禁用户并释放座位。
     */
    @Transactional
    public int releaseTimeoutReservations() {
        int limitMinutes = configService.getIntConfig(BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES, 15);
        int violationLimit = configService.getIntConfig(BizConstants.CONFIG_VIOLATION_LIMIT, 3);
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> pendingReservations = reservationMapper.findAllPending();
        int handled = 0;
        for (Reservation reservation : pendingReservations) {
            LocalDateTime deadline = LocalDateTime.of(reservation.getReservationDate(), reservation.getStartTime())
                    .plusMinutes(limitMinutes);
            if (!now.isAfter(deadline)) {
                continue;
            }
            int changed = reservationMapper.updateStatusIfCurrent(reservation.getId(),
                    BizConstants.RESERVATION_PENDING, BizConstants.RESERVATION_VIOLATED);
            if (changed == 0) {
                continue;
            }
            reservationSlotOccupancyMapper.deleteByReservationId(
                    reservation.getId()
            );
            Violation violation = new Violation();
            violation.setUserId(reservation.getUserId());
            violation.setReservationId(reservation.getId());
            violation.setViolationType(BizConstants.VIOLATION_TIMEOUT_CHECKIN);
            violation.setReason("超时未签到");
            violation.setHandleResult("记录违规一次");
            violationMapper.insert(violation);
            userMapper.increaseViolation(reservation.getUserId());
            User user = userMapper.findById(reservation.getUserId());
            if (user != null && user.getViolationCount() != null && user.getViolationCount() >= violationLimit) {
                userMapper.banUser(reservation.getUserId());
            }
            roomStatsService.refreshRoomSeatStats(reservation.getRoomId());
            handled++;
        }
        return handled;
    }
}
