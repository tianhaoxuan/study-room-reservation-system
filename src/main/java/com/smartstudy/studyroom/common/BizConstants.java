package com.smartstudy.studyroom.common;

public final class BizConstants {

    public static final int USER_STATUS_NORMAL = 1;
    public static final int USER_STATUS_BANNED = 0;

    public static final int SEAT_STATUS_FREE = 1;
    public static final int SEAT_STATUS_RESERVED = 2;
    public static final int SEAT_STATUS_USING = 3;
    public static final int SEAT_STATUS_REPAIR = 4;

    public static final int RESERVATION_PENDING = 1;
    public static final int RESERVATION_USING = 2;
    public static final int RESERVATION_FINISHED = 3;
    public static final int RESERVATION_CANCELED = 4;
    public static final int RESERVATION_VIOLATED = 5;

    public static final int VIOLATION_NO_SHOW = 1;
    public static final int VIOLATION_TIMEOUT_CHECKIN = 2;

    public static final String CONFIG_CHECKIN_LIMIT_MINUTES = "checkin_limit_minutes";
    public static final String CONFIG_MAX_RESERVATION_PER_DAY = "max_reservation_per_day";
    public static final String CONFIG_VIOLATION_LIMIT = "violation_limit";

    private BizConstants() {
    }
}
