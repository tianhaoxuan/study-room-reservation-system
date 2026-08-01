# Redis Bitmap Seat Occupancy Projection

## Purpose

Redis Bitmap is used as a high-performance query projection for seat occupancy.

It is not the final source of truth. MySQL remains the authoritative storage for reservations and atomic slot occupancy records.

The final correctness boundary is still enforced by MySQL unique constraints on:

- seat_id + reservation_date + slot_id
- user_id + reservation_date + slot_id

Redis only helps seat-map queries avoid repeatedly scanning MySQL occupancy records.

## Key Model

Each room, date, and slot owns one Bitmap key.

```text
seat:bitmap:{roomId}:{reservationDate}:{slotId}