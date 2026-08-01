package com.smartstudy.studyroom.mapper;

import com.smartstudy.studyroom.entity.ReservationTimeoutMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReservationTimeoutMessageMapper {

    @Insert("""
            INSERT INTO reservation_timeout_message(
                reservation_id,
                deadline_at,
                status
            )
            VALUES(
                #{reservationId},
                #{deadlineAt},
                #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReservationTimeoutMessage message);

    @Update("""
            UPDATE reservation_timeout_message
            SET status = #{sentStatus},
                sent_time = NOW(),
                last_error = NULL
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
                last_error = #{lastError}
            WHERE id = #{id}
              AND status <> #{sentStatus}
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("failedStatus") Integer failedStatus,
            @Param("sentStatus") Integer sentStatus,
            @Param("lastError") String lastError
    );
}