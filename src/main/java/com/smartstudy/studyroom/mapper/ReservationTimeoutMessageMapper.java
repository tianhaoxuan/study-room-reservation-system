package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationTimeoutMessageMapper {

    @Insert("""
            INSERT INTO reservation_timeout_message(
                reservation_id,
                deadline_at,
                status,
                next_retry_time
            )
            VALUES(
                #{reservationId},
                #{deadlineAt},
                #{status},
                #{nextRetryTime}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReservationTimeoutMessage message);

    @Update("""
            UPDATE reservation_timeout_message
            SET status = #{sentStatus},
                sent_time = NOW(),
                last_error = NULL,
                next_retry_time = NULL
            WHERE id = #{id}
              AND status IN (#{pendingStatus}, #{failedStatus})
            """)
    int markSent(
            @Param("id") Long id,
            @Param("pendingStatus") Integer pendingStatus,
            @Param("failedStatus") Integer failedStatus,
            @Param("sentStatus") Integer sentStatus
    );

    @Update("""
            UPDATE reservation_timeout_message
            SET status = #{failedStatus},
                retry_count = retry_count + 1,
                next_retry_time = #{nextRetryTime},
                last_error = #{lastError}
            WHERE id = #{id}
              AND status NOT IN (
                    #{sentStatus},
                    #{consumedStatus},
                    #{deadLetterStatus}
              )
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("failedStatus") Integer failedStatus,
            @Param("sentStatus") Integer sentStatus,
            @Param("consumedStatus") Integer consumedStatus,
            @Param("deadLetterStatus") Integer deadLetterStatus,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastError") String lastError
    );

    @Update("""
            UPDATE reservation_timeout_message
            SET status = #{consumedStatus},
                consumed_time = NOW(),
                last_error = NULL,
                next_retry_time = NULL
            WHERE id = #{id}
              AND status IN (#{pendingStatus}, #{failedStatus}, #{sentStatus})
            """)
    int markConsumed(
            @Param("id") Long id,
            @Param("pendingStatus") Integer pendingStatus,
            @Param("failedStatus") Integer failedStatus,
            @Param("sentStatus") Integer sentStatus,
            @Param("consumedStatus") Integer consumedStatus
    );

    @Update("""
            UPDATE reservation_timeout_message
            SET status = #{deadLetterStatus},
                dead_letter_time = NOW(),
                next_retry_time = NULL,
                last_error = #{lastError}
            WHERE id = #{id}
              AND status IN (#{pendingStatus}, #{failedStatus}, #{sentStatus})
            """)
    int markDeadLetter(
            @Param("id") Long id,
            @Param("pendingStatus") Integer pendingStatus,
            @Param("failedStatus") Integer failedStatus,
            @Param("sentStatus") Integer sentStatus,
            @Param("deadLetterStatus") Integer deadLetterStatus,
            @Param("lastError") String lastError
    );

    @Select("""
            SELECT *
            FROM reservation_timeout_message
            WHERE status IN (#{pendingStatus}, #{failedStatus})
              AND retry_count < #{maxRetryCount}
              AND (
                    next_retry_time IS NULL
                    OR next_retry_time <= #{now}
              )
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<ReservationTimeoutMessage> findRetryable(
            @Param("pendingStatus") Integer pendingStatus,
            @Param("failedStatus") Integer failedStatus,
            @Param("maxRetryCount") Integer maxRetryCount,
            @Param("now") LocalDateTime now,
            @Param("limit") Integer limit
    );
}