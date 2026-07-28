package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.PageResult;
import com.smartstudy.studyroom.common.StatusCode;
import com.smartstudy.studyroom.dto.CreateReservationRequest;
import com.smartstudy.studyroom.dto.CreateReservationResponse;
import com.smartstudy.studyroom.dto.MyReservationResponse;
import com.smartstudy.studyroom.entity.Reservation;
import com.smartstudy.studyroom.entity.Seat;
import com.smartstudy.studyroom.entity.StudyRoom;
import com.smartstudy.studyroom.entity.User;
import com.smartstudy.studyroom.exception.BusinessException;
import com.smartstudy.studyroom.mapper.ReservationMapper;
import com.smartstudy.studyroom.mapper.SeatMapper;
import com.smartstudy.studyroom.mapper.StudyRoomMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final StudyRoomMapper studyRoomMapper;
    private final UserService userService;
    private final ConfigService configService;
    private final RoomStatsService roomStatsService;

    public ReservationService(ReservationMapper reservationMapper,
                              SeatMapper seatMapper,
                              StudyRoomMapper studyRoomMapper,
                              UserService userService,
                              ConfigService configService,
                              RoomStatsService roomStatsService) {
        this.reservationMapper = reservationMapper;
        this.seatMapper = seatMapper;
        this.studyRoomMapper = studyRoomMapper;
        this.userService = userService;
        this.configService = configService;
        this.roomStatsService = roomStatsService;
    }

    /**
     * 功能：创建预约。
     * 请求参数：seatId、roomId、reservationDate、timeSlot、startTime、endTime。
     * 返回值：reservationId 和预约状态。
     * 核心逻辑说明：校验用户状态、座位状态、座位冲突、用户同一时段冲突和每日限约次数后写入预约，并更新座位/房间状态。
     */
    @Transactional
    public CreateReservationResponse createReservation(Long userId, CreateReservationRequest request) {
        User user = userService.requireUser(userId);
        if (!Integer.valueOf(BizConstants.USER_STATUS_NORMAL).equals(user.getStatus())) {
            throw new BusinessException(StatusCode.FORBIDDEN, "账号已封禁，暂不能预约");
        }
        if (request.getReservationDate().isBefore(LocalDate.now())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "不能预约过去日期");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }

        Seat seat = seatMapper.findById(request.getSeatId());
        if (seat == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "座位不存在");
        }
        if (!request.getRoomId().equals(seat.getRoomId())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "座位不属于当前自习室");
        }
        if (Integer.valueOf(BizConstants.SEAT_STATUS_REPAIR).equals(seat.getStatus())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "维修中的座位不能预约");
        }
        StudyRoom room = studyRoomMapper.findById(request.getRoomId());
        if (room == null || room.getStatus() == null || room.getStatus() != 1) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "自习室不存在或未开放");
        }

        if (reservationMapper.countSeatConflict(request.getSeatId(), request.getReservationDate(), request.getTimeSlot()) > 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "该座位在当前时段已被预约");
        }
        if (reservationMapper.countUserSlotConflict(userId, request.getReservationDate(), request.getTimeSlot()) > 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "同一时段只能预约一个座位");
        }
        int maxDaily = configService.getIntConfig(BizConstants.CONFIG_MAX_RESERVATION_PER_DAY, 2);
        if (reservationMapper.countUserDailyActive(userId, request.getReservationDate()) >= maxDaily) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "当天预约次数已达上限");
        }

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSeatId(request.getSeatId());
        reservation.setRoomId(request.getRoomId());
        reservation.setReservationDate(request.getReservationDate());
        reservation.setTimeSlot(request.getTimeSlot());
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());
        reservation.setStatus(BizConstants.RESERVATION_PENDING);
        reservationMapper.insert(reservation);
        seatMapper.updateStatus(request.getSeatId(), BizConstants.SEAT_STATUS_RESERVED);
        roomStatsService.refreshRoomSeatStats(request.getRoomId());
        return new CreateReservationResponse(reservation.getId(), reservation.getStatus());
    }

    /**
     * 功能：取消预约。
     * 请求参数：reservationId。
     * 返回值：无。
     * 核心逻辑说明：只能取消本人待签到预约；取消后释放座位并刷新自习室统计。
     */
    @Transactional
    public void cancelReservation(Long userId, Long reservationId) {
        Reservation reservation = requireOwnReservation(userId, reservationId);
        if (!Integer.valueOf(BizConstants.RESERVATION_PENDING).equals(reservation.getStatus())) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "只有待签到预约可以取消");
        }
        int changed = reservationMapper.updateStatusIfCurrent(reservationId,
                BizConstants.RESERVATION_PENDING, BizConstants.RESERVATION_CANCELED);
        if (changed == 0) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "预约状态已变化，请刷新后重试");
        }
        releaseSeatIfNoActiveReservation(reservation.getSeatId());
        roomStatsService.refreshRoomSeatStats(reservation.getRoomId());
    }

    /**
     * 功能：查询我的预约。
     * 请求参数：status、pageNum、pageSize。
     * 返回值：分页预约记录，包含楼栋、自习室、座位和时段信息。
     * 核心逻辑说明：按当前用户ID查询，可选状态筛选，默认第1页每页10条。
     */
    public PageResult<MyReservationResponse> findMyReservations(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;
        long total = reservationMapper.countMy(userId, status);
        List<MyReservationResponse> records = reservationMapper.findMy(userId, status, offset, safePageSize);
        return new PageResult<MyReservationResponse>(total, records);
    }

    public Reservation requireOwnReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationMapper.findById(reservationId);
        if (reservation == null) {
            throw new BusinessException(StatusCode.PARAM_ERROR, "预约不存在");
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new BusinessException(StatusCode.FORBIDDEN, "只能操作自己的预约");
        }
        return reservation;
    }

    public void releaseSeatIfNoActiveReservation(Long seatId) {
        if (reservationMapper.countActiveBySeat(seatId) == 0) {
            seatMapper.updateStatus(seatId, BizConstants.SEAT_STATUS_FREE);
        }
    }

    public void releaseSeatIfNoOtherActiveReservation(Long seatId, Long reservationId) {
        if (reservationMapper.countActiveBySeatExclude(seatId, reservationId) == 0) {
            seatMapper.updateStatus(seatId, BizConstants.SEAT_STATUS_FREE);
        }
    }
}
