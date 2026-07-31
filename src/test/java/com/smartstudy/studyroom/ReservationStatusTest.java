package com.smartstudy.studyroom;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.common.ReservationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationStatusTest {

    @Test
    void pendingCheckinAllowsExpectedTransitions() {
        assertThat(ReservationStatus.PENDING_CHECKIN.canTransitionTo(
                ReservationStatus.IN_USE
        )).isTrue();
        assertThat(ReservationStatus.PENDING_CHECKIN.canTransitionTo(
                ReservationStatus.CANCELLED
        )).isTrue();
        assertThat(ReservationStatus.PENDING_CHECKIN.canTransitionTo(
                ReservationStatus.VIOLATED
        )).isTrue();
    }

    @Test
    void terminalStatusesRejectFurtherTransitions() {
        assertThat(ReservationStatus.COMPLETED.canTransitionTo(
                ReservationStatus.IN_USE
        )).isFalse();
        assertThat(ReservationStatus.CANCELLED.canTransitionTo(
                ReservationStatus.IN_USE
        )).isFalse();
        assertThat(ReservationStatus.VIOLATED.canTransitionTo(
                ReservationStatus.IN_USE
        )).isFalse();
    }

    @Test
    void convertsDatabaseCodeToStatus() {
        assertThat(ReservationStatus.fromCode(
                BizConstants.RESERVATION_PENDING
        )).isEqualTo(ReservationStatus.PENDING_CHECKIN);
        assertThat(ReservationStatus.fromCode(
                BizConstants.RESERVATION_USING
        )).isEqualTo(ReservationStatus.IN_USE);

        assertThatThrownBy(() -> ReservationStatus.fromCode(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown reservation status: 99");
    }
}
