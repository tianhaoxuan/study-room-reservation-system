package com.smartstudy.studyroom.task;

import com.smartstudy.studyroom.service.ReservationTimeoutService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationTimeoutTask {

    private final ReservationTimeoutService reservationTimeoutService;

    public ReservationTimeoutTask(ReservationTimeoutService reservationTimeoutService) {
        this.reservationTimeoutService = reservationTimeoutService;
    }

    /**
     * 功能：超时释放定时任务。
     * 请求参数：无，系统后台每分钟自动执行。
     * 返回值：无。
     * 核心逻辑说明：扫描待签到预约，超过配置的签到宽容时间后自动标记违约、记录违规、封禁达到阈值的用户并释放座位。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void releaseTimeoutReservations() {
        reservationTimeoutService.releaseTimeoutReservations();
    }
}
