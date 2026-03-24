-- V14__protect_active_booking.sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_active_booking_per_user_session
ON bookings (session_id, user_id)
WHERE status = 'BOOKED';