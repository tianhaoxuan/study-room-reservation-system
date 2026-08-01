package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.mapper.ReservationTimeoutMessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReservationTimeoutMessageService {

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_FAILED = 3;

    private static final int MAX_ERROR_LENGTH = 500;

    private final ReservationTimeoutMessageMapper mapper;

    public ReservationTimeoutMessageService(
            ReservationTimeoutMessageMapper mapper) {

        this.mapper = mapper;
    }

    public ReservationTimeoutMessage createPending(
            Long reservationId,
            LocalDateTime deadlineAt) {

        ReservationTimeoutMessage message =
                new ReservationTimeoutMessage();
        message.setReservationId(reservationId);
        message.setDeadlineAt(deadlineAt);
        message.setStatus(STATUS_PENDING);

        mapper.insert(message);
        return message;
    }

    public void markSent(Long messageId) {
        mapper.markSent(
                messageId,
                STATUS_PENDING,
                STATUS_FAILED,
                STATUS_SENT
        );
    }

    public void markFailed(Long messageId, String errorMessage) {
        mapper.markFailed(
                messageId,
                STATUS_FAILED,
                STATUS_SENT,
                truncate(errorMessage)
        );
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}