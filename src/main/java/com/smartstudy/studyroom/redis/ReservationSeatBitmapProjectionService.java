package com.smartstudy.studyroom.redis;

import com.smartstudy.studyroom.entity.ReservationSlotOccupancy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

@Service
public class ReservationSeatBitmapProjectionService {

    private final SeatOccupancyBitmapService bitmapService;
    private final boolean enabled;

    public ReservationSeatBitmapProjectionService(
            SeatOccupancyBitmapService bitmapService,
            @Value("${studyroom.redis.seat-occupancy.enabled:true}")
            boolean enabled) {

        this.bitmapService = bitmapService;
        this.enabled = enabled;
    }

    public void projectOccupiedAfterCommit(
            Collection<ReservationSlotOccupancy> occupancies) {

        if (!enabled || occupancies == null || occupancies.isEmpty()) {
            return;
        }

        runAfterCommit(() -> {
            for (ReservationSlotOccupancy occupancy : occupancies) {
                bitmapService.occupy(
                        occupancy.getRoomId(),
                        occupancy.getReservationDate(),
                        occupancy.getSlotId(),
                        occupancy.getSeatId()
                );
            }
        });
    }

    public void projectReleasedAfterCommit(
            Collection<ReservationSlotOccupancy> occupancies) {

        if (!enabled || occupancies == null || occupancies.isEmpty()) {
            return;
        }

        runAfterCommit(() -> {
            for (ReservationSlotOccupancy occupancy : occupancies) {
                bitmapService.release(
                        occupancy.getRoomId(),
                        occupancy.getReservationDate(),
                        occupancy.getSlotId(),
                        occupancy.getSeatId()
                );
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );
    }
}