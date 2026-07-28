USE zixishi;

SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO building (id, building_name, status) VALUES
(1, 'Library Building 1', 1),
(2, 'Teaching Building A', 1),
(3, 'Lab Building', 1)
ON DUPLICATE KEY UPDATE
building_name = VALUES(building_name),
status = VALUES(status);

INSERT INTO study_room (id, building_id, room_name, total_seats, reserved_seats, occupancy_rate, open_time, close_time, status) VALUES
(1, 1, 'Silent Room 101', 12, 2, 16.67, '08:00:00', '22:30:00', 1),
(2, 1, 'Exam Room 201', 16, 3, 18.75, '07:30:00', '23:00:00', 1),
(3, 2, 'Open Study Area A', 10, 1, 10.00, '08:00:00', '21:30:00', 1),
(4, 3, 'Lab Quiet Area', 8, 0, 0.00, '09:00:00', '21:00:00', 1)
ON DUPLICATE KEY UPDATE
building_id = VALUES(building_id),
room_name = VALUES(room_name),
total_seats = VALUES(total_seats),
reserved_seats = VALUES(reserved_seats),
occupancy_rate = VALUES(occupancy_rate),
open_time = VALUES(open_time),
close_time = VALUES(close_time),
status = VALUES(status);

INSERT INTO seat (id, room_id, seat_no, x, y, has_power, near_window, status) VALUES
(1, 1, 'A001', 1, 1, 1, 1, 2),
(2, 1, 'A002', 2, 1, 1, 1, 1),
(3, 1, 'A003', 3, 1, 0, 1, 1),
(4, 1, 'A004', 4, 1, 0, 0, 1),
(5, 1, 'A005', 1, 2, 1, 0, 3),
(6, 1, 'A006', 2, 2, 1, 0, 1),
(7, 1, 'A007', 3, 2, 0, 0, 1),
(8, 1, 'A008', 4, 2, 0, 0, 4),
(9, 1, 'A009', 1, 3, 1, 1, 1),
(10, 1, 'A010', 2, 3, 1, 1, 1),
(11, 1, 'A011', 3, 3, 0, 1, 1),
(12, 1, 'A012', 4, 3, 0, 1, 1),
(13, 2, 'B001', 1, 1, 1, 1, 2),
(14, 2, 'B002', 2, 1, 1, 1, 1),
(15, 2, 'B003', 3, 1, 1, 0, 1),
(16, 2, 'B004', 4, 1, 1, 0, 1),
(17, 2, 'B005', 1, 2, 0, 1, 1),
(18, 2, 'B006', 2, 2, 0, 1, 1),
(19, 2, 'B007', 3, 2, 0, 0, 3),
(20, 2, 'B008', 4, 2, 0, 0, 1),
(21, 3, 'C001', 1, 1, 1, 0, 1),
(22, 3, 'C002', 2, 1, 1, 0, 2),
(23, 3, 'C003', 3, 1, 0, 0, 1),
(24, 3, 'C004', 4, 1, 0, 0, 1),
(25, 4, 'D001', 1, 1, 1, 1, 1),
(26, 4, 'D002', 2, 1, 1, 1, 1),
(27, 4, 'D003', 3, 1, 0, 0, 1),
(28, 4, 'D004', 4, 1, 0, 0, 1)
ON DUPLICATE KEY UPDATE
room_id = VALUES(room_id),
seat_no = VALUES(seat_no),
x = VALUES(x),
y = VALUES(y),
has_power = VALUES(has_power),
near_window = VALUES(near_window),
status = VALUES(status);

INSERT INTO `user` (id, openid, student_no, real_name, nickname, avatar_url, credit_score, violation_count, status) VALUES
(1, 'openid-test-001', '20240001', 'Zhang San', 'student001', 'https://example.com/avatar/001.png', 100, 0, 1),
(2, 'openid-test-002', '20240002', 'Li Si', 'student002', 'https://example.com/avatar/002.png', 95, 1, 1),
(3, 'openid-test-003', '20240003', 'Wang Wu', 'student003', 'https://example.com/avatar/003.png', 80, 2, 1),
(4, 'openid-test-004', '20240004', 'Zhao Liu', 'student004', 'https://example.com/avatar/004.png', 100, 0, 1),
(5, 'openid-test-005', '20240005', 'Qian Qi', 'student005', 'https://example.com/avatar/005.png', 60, 4, 0)
ON DUPLICATE KEY UPDATE
openid = VALUES(openid),
student_no = VALUES(student_no),
real_name = VALUES(real_name),
nickname = VALUES(nickname),
avatar_url = VALUES(avatar_url),
credit_score = VALUES(credit_score),
violation_count = VALUES(violation_count),
status = VALUES(status);

INSERT INTO reservation (id, user_id, seat_id, room_id, reservation_date, time_slot, start_time, end_time, status, sign_time, leave_time, create_time) VALUES
(1, 1, 1, 1, CURRENT_DATE, '08:00-10:00', '08:00:00', '10:00:00', 1, NULL, NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(2, 2, 5, 1, CURRENT_DATE, '10:00-12:00', '10:00:00', '12:00:00', 2, DATE_SUB(NOW(), INTERVAL 20 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3, 3, 13, 2, CURRENT_DATE, '14:00-16:00', '14:00:00', '16:00:00', 1, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(4, 4, 22, 3, DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY), '09:00-11:00', '09:00:00', '11:00:00', 1, NULL, NULL, NOW()),
(5, 1, 2, 1, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), '19:00-21:00', '19:00:00', '21:00:00', 3, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 23 HOUR), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(6, 2, 14, 2, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), '08:00-10:00', '08:00:00', '10:00:00', 5, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(7, 3, 15, 2, DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY), '13:00-15:00', '13:00:00', '15:00:00', 4, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY))
ON DUPLICATE KEY UPDATE
user_id = VALUES(user_id),
seat_id = VALUES(seat_id),
room_id = VALUES(room_id),
reservation_date = VALUES(reservation_date),
time_slot = VALUES(time_slot),
start_time = VALUES(start_time),
end_time = VALUES(end_time),
status = VALUES(status),
sign_time = VALUES(sign_time),
leave_time = VALUES(leave_time),
create_time = VALUES(create_time);

INSERT INTO violation (id, user_id, reservation_id, violation_type, reason, handle_result, create_time) VALUES
(1, 2, 6, 1, 'No check-in after reservation', 'Deduct 5 credit points', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 3, 7, 2, 'Check-in timeout', 'Deduct 10 credit points', DATE_SUB(NOW(), INTERVAL 2 DAY))
ON DUPLICATE KEY UPDATE
user_id = VALUES(user_id),
reservation_id = VALUES(reservation_id),
violation_type = VALUES(violation_type),
reason = VALUES(reason),
handle_result = VALUES(handle_result),
create_time = VALUES(create_time);

INSERT INTO system_config (id, config_key, config_value, description) VALUES
(1, 'checkin_limit_minutes', '15', 'Timeout minutes after reservation start'),
(2, 'violation_limit', '3', 'Violation count limit before ban'),
(3, 'max_reservation_per_day', '3', 'Max active reservations per user per day'),
(4, 'reservation_max_hours', '4', 'Max hours per reservation'),
(5, 'cancel_before_minutes', '30', 'Minutes allowed before cancel'),
(6, 'violation_deduct_score', '5', 'Credit score deducted per violation')
ON DUPLICATE KEY UPDATE
config_key = VALUES(config_key),
config_value = VALUES(config_value),
description = VALUES(description);

INSERT INTO notice (id, title, content) VALUES
(1, 'System Trial Notice', 'The study room reservation system is now in trial operation.'),
(2, 'Check-in Reminder', 'Please scan the QR code to check in within the allowed time.'),
(3, 'Study Room Rules', 'Please keep quiet and release your seat before leaving.')
ON DUPLICATE KEY UPDATE
title = VALUES(title),
content = VALUES(content);

SET FOREIGN_KEY_CHECKS = 1;
