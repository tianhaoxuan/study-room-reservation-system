package com.smartstudy.studyroom.redis;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SeatPreOccupyPayload(
        String requestId,
        Long userId,
        Long roomId,
        LocalDate reservationDate,
        Long seatId,
        List<Long> slotIds) {

    public static SeatPreOccupyPayload of(
            String requestId,
            Long userId,
            Long roomId,
            LocalDate reservationDate,
            Collection<Long> slotIds,
            Long seatId) {

        return new SeatPreOccupyPayload(
                requestId.strip(),
                userId,
                roomId,
                reservationDate,
                seatId,
                normalizeSlotIds(slotIds)
        );
    }

    public String encode() {
        return requestId
                + "|"
                + userId
                + "|"
                + roomId
                + "|"
                + reservationDate
                + "|"
                + seatId
                + "|"
                + String.join(
                ",",
                slotIds.stream()
                        .map(String::valueOf)
                        .toList()
        );
    }

    public static Optional<SeatPreOccupyPayload> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String[] parts = value.split("\\|", -1);
        if (parts.length != 6) {
            return Optional.empty();
        }

        try {
            String requestId = parts[0];
            Long userId = Long.valueOf(parts[1]);
            Long roomId = Long.valueOf(parts[2]);
            LocalDate reservationDate = LocalDate.parse(parts[3]);
            Long seatId = Long.valueOf(parts[4]);
            List<Long> slotIds = parseSlotIds(parts[5]);

            if (requestId.isBlank() || slotIds.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new SeatPreOccupyPayload(
                    requestId,
                    userId,
                    roomId,
                    reservationDate,
                    seatId,
                    slotIds
            ));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static List<Long> parseSlotIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return normalizeSlotIds(
                List.of(value.split(","))
                        .stream()
                        .map(String::strip)
                        .filter(item -> !item.isBlank())
                        .map(Long::valueOf)
                        .toList()
        );
    }

    private static List<Long> normalizeSlotIds(Collection<Long> slotIds) {
        if (slotIds == null) {
            return List.of();
        }

        return slotIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}