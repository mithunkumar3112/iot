-- ============================================================
-- QUICK SQL COMMANDS FOR SUPABASE - COPY & PASTE DIRECTLY
-- ============================================================
-- Use this file when you're in Supabase SQL Editor
-- Run each section one at a time

-- ============================================================
-- SECTION 1: CREATE ALL TABLES
-- ============================================================
-- Copy everything from db/supabase_setup.sql and paste here
-- See that file for the complete schema

-- ============================================================
-- SECTION 2: INSERT TEST DATA
-- ============================================================

-- Test User
INSERT INTO users (username, password, email) 
VALUES ('testuser', 'hashedpassword123', 'test@example.com')
ON CONFLICT (email) DO NOTHING;

-- Test Device
INSERT INTO devices (id, name, mac_address, user_id, is_online)
VALUES ('device-001', 'Test Laptop', 'AA:BB:CC:DD:EE:FF', 1, true)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- SECTION 3: ENABLE RLS (For development, allow public access)
-- ============================================================

CREATE POLICY "Allow public read/write" ON alerts
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON usb_activity
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON security_screenshots
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON login_sessions
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON devices
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON users
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON files_metadata
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON process_logs
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON app_detections
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON system_metrics
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON active_window_activity
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON commands
  FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read/write" ON screenshots
  FOR ALL USING (true) WITH CHECK (true);

-- ============================================================
-- SECTION 4: ADD SAMPLE SECURITY ALERTS
-- ============================================================

INSERT INTO alerts (device_id, device_name, message, severity, type, source, process_name, timestamp)
VALUES 
  ('device-001', 'Test Laptop', 'Excel detected - sensitive app running', 'HIGH', 'SCREENSHOT_ACTIVITY', 'agent', 'excel.exe', NOW()),
  ('device-001', 'Test Laptop', 'Suspicious process detected: powershell.exe', 'MEDIUM', 'PROCESS_ALERT', 'agent', 'powershell.exe', NOW() - INTERVAL '5 minutes'),
  ('device-001', 'Test Laptop', 'Unauthorized USB device connected', 'CRITICAL', 'USB_ALERT', 'agent', NULL, NOW() - INTERVAL '10 minutes'),
  ('device-001', 'Test Laptop', 'Failed login attempt detected', 'HIGH', 'LOGIN_ALERT', 'agent', NULL, NOW() - INTERVAL '15 minutes');

-- ============================================================
-- SECTION 5: ADD SAMPLE USB ACTIVITY
-- ============================================================

INSERT INTO usb_activity (device_id, device_name, connection_type, status, timestamp)
VALUES 
  ('device-001', 'Kingston USB 3.0 Drive', 'USB 3.0', 'CONNECTED', NOW()),
  ('device-001', 'WD External HDD', 'USB 2.0', 'DISCONNECTED', NOW() - INTERVAL '2 minutes'),
  ('device-001', 'HP Printer', 'USB', 'CONNECTED', NOW() - INTERVAL '1 hour');

-- ============================================================
-- SECTION 6: ADD SAMPLE LOGIN SESSIONS
-- ============================================================

INSERT INTO login_sessions (device_id, username, login_time, status)
VALUES 
  ('device-001', 'admin', NOW() - INTERVAL '1 hour', 'ACTIVE'),
  ('device-001', 'guest', NOW() - INTERVAL '3 hours', 'LOGGED_OUT'),
  ('device-001', 'developer', NOW() - INTERVAL '30 minutes', 'ACTIVE');

-- ============================================================
-- SECTION 7: ADD SAMPLE FILES
-- ============================================================

INSERT INTO files_metadata (device_id, file_name, file_url, storage_path, file_size, file_type)
VALUES 
  ('device-001', 'report.pdf', 'https://oavizsbcurtnsekuwebj.supabase.co/storage/v1/object/public/monitor-files/device-001/report.pdf', 'device-001/report.pdf', 1024000, 'pdf'),
  ('device-001', 'data.xlsx', 'https://oavizsbcurtnsekuwebj.supabase.co/storage/v1/object/public/monitor-files/device-001/data.xlsx', 'device-001/data.xlsx', 512000, 'xlsx'),
  ('device-001', 'presentation.pptx', 'https://oavizsbcurtnsekuwebj.supabase.co/storage/v1/object/public/monitor-files/device-001/presentation.pptx', 'device-001/presentation.pptx', 2048000, 'pptx');

-- ============================================================
-- SECTION 8: VERIFICATION QUERIES
-- ============================================================
-- Run these to verify data was inserted

-- Count records in each table
SELECT 'alerts' as table_name, COUNT(*) as count FROM alerts
UNION ALL
SELECT 'usb_activity', COUNT(*) FROM usb_activity
UNION ALL
SELECT 'login_sessions', COUNT(*) FROM login_sessions
UNION ALL
SELECT 'files_metadata', COUNT(*) FROM files_metadata
UNION ALL
SELECT 'devices', COUNT(*) FROM devices
UNION ALL
SELECT 'users', COUNT(*) FROM users;

-- ============================================================
-- SECTION 9: TROUBLESHOOTING - VIEW RECENT DATA
-- ============================================================

-- View latest alerts
SELECT id, device_id, message, severity, timestamp FROM alerts ORDER BY timestamp DESC LIMIT 5;

-- View USB activity
SELECT id, device_id, device_name, status, timestamp FROM usb_activity ORDER BY timestamp DESC;

-- View files
SELECT id, device_id, file_name, file_url FROM files_metadata;

-- View devices
SELECT id, name, mac_address, is_online FROM devices;

-- ============================================================
-- SECTION 10: CLEANUP (Only if you need to reset)
-- ============================================================
-- Uncomment these if you want to delete test data

-- DELETE FROM alerts WHERE device_id = 'device-001';
-- DELETE FROM usb_activity WHERE device_id = 'device-001';
-- DELETE FROM login_sessions WHERE device_id = 'device-001';
-- DELETE FROM files_metadata WHERE device_id = 'device-001';
-- DELETE FROM devices WHERE id = 'device-001';
-- DELETE FROM users WHERE email = 'test@example.com';

-- ============================================================
-- END OF QUICK REFERENCE SCRIPT
-- ============================================================
