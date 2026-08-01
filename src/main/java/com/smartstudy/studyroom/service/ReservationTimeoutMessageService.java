package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import com.smartstudy.studyroom.mapper.ReservationTimeoutMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationTimeoutMessageService {

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_FAILED = 3;

    private static final int MAX_ERROR_LENGTH = 500;
    private static final int DEFAULT_RETRY_DELAY_MINUTES = 1;

    private final ReservationTimeoutMessageMapper mapper;
    private final Clock clock;

    @Autowired
    public ReservationTimeoutMessageService(
            ReservationTimeoutMessageMapper mapper) {

        this(mapper, Clock.systemDefaultZone());
    }

    public ReservationTimeoutMessageService(
            ReservationTimeoutMessageMapper mapper,
            Clock clock) {

        this.mapper = mapper;
        this.clock = clock;
    }

    public ReservationTimeoutMessage createPending(
            Long reservationId,
            LocalDateTime deadlineAt) {

        ReservationTimeoutMessage message =
                new ReservationTimeoutMessage();
        message.setReservationId(reservationId);
        message.setDeadlineAt(deadlineAt);
        message.setStatus(STATUS_PENDING);
        message.setNextRetryTime(LocalDateTime.now(clock));

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
                LocalDateTime.now(clock).plusMinutes(
                        DEFAULT_RETRY_DELAY_MINUTES
                ),
                truncate(errorMessage)
        );
    }

    public List<ReservationTimeoutMessage> findRetryable(
            int maxRetryCount,
            int limit) {

        return mapper.findRetryable(
                STATUS_PENDING,
                STATUS_FAILED,
                maxRetryCount,
                LocalDateTime.now(clock),
                limit
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