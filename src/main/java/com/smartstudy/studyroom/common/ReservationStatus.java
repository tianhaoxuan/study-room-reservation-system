package com.smartstudy.studyroom.common;

import java.util.Arrays;
import java.util.List;

public enum ReservationStatus {

    PENDING_CHECKIN(BizConstants.RESERVATION_PENDING),
    IN_USE(BizConstants.RESERVATION_USING),
    COMPLETED(BizConstants.RESERVATION_FINISHED),
    CANCELLED(BizConstants.RESERVATION_CANCELED),
    VIOLATED(BizConstants.RESERVATION_VIOLATED);

    private final int code;

    ReservationStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean canTransitionTo(ReservationStatus target) {
        return switch (this) {
            case PENDING_CHECKIN ->
                    target == IN_USE
                            || target == CANCELLED
                            || target == VIOLATED;
            case IN_USE -> target == COMPLETED;
            case COMPLETED, CANCELLED, VIOLATED -> false;
        };
    }

    public boolean canBeCancelledByAdmin() {
        return this == PENDING_CHECKIN || this == IN_USE;
    }

    public static ReservationStatus fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("Reservation status cannot be null");
        }

        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown reservation status: " + code
                ));
    }

    public static List<Integer> adminCancellableCodes() {
        return List.of(
                PENDING_CHECKIN.code,
                IN_USE.code
        );
    }
}
