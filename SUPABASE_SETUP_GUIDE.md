# Supabase Setup Guide - Complete Fix for Screenshots, Security, and File Explorer

## Problem Summary
Your IoT Monitor application only shows screenshots but security alerts and file explorer aren't working. This is because the Supabase database schema is incomplete—it's missing essential tables for alerts, USB activity, login sessions, and devices.

---

## Step 1: Run the Complete Schema in Supabase

1. **Log in to Supabase**: Go to https://supabase.com and log in to your project
   - URL: `https://oavizsbcurtnsekuwebj.supabase.co`

2. **Navigate to SQL Editor**: Click on the **SQL Editor** in the left sidebar

3. **Create a new query** by clicking **+ New Query**

4. **Copy and paste the entire content** from `/db/supabase_setup.sql` into the SQL editor

5. **Run the query** by clicking the **Run** button (or Ctrl+Enter)

6. **Verify all tables were created**:
   - Go to the **Table Editor** in the left sidebar
   - You should see these tables:
     ```
     users
     devices
     alerts
     usb_activity
     security_screenshots
     login_sessions
     files_metadata
     process_logs
     app_detections
     system_metrics
     usage_analytics
     active_window_activity
     commands
     screenshots
     ```

---

## Step 2: Configure Row Level Security (RLS) - Optional for Development

For **development/testing**, you can temporarily allow public access:

```sql
-- Run these commands in Supabase SQL Editor

-- Create public policy for alerts
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
```

---

## Step 3: Populate Test Data

Run this script in Supabase SQL Editor to add sample data:

```sql
-- Insert test user
INSERT INTO users (username, password, email) 
VALUES ('testuser', 'hashedpassword123', 'test@example.com')
ON CONFLICT (email) DO NOTHING;

-- Insert test device
INSERT INTO devices (id, name, mac_address, user_id, is_online)
VALUES ('device-001', 'Test Laptop', 'AA:BB:CC:DD:EE:FF', 1, true)
ON CONFLICT (id) DO NOTHING;

-- Insert test security alerts
INSERT INTO alerts (device_id, device_name, message, severity, type, source, process_name, timestamp)
VALUES 
  ('device-001', 'Test Laptop', 'Suspicious process detected', 'HIGH', 'PROCESS_ALERT', 'agent', 'suspicious.exe', NOW()),
  ('device-001', 'Test Laptop', 'USB device connected', 'MEDIUM', 'USB_ALERT', 'agent', NULL, NOW() - INTERVAL '5 minutes'),
  ('device-001', 'Test Laptop', 'Unauthorized access attempt', 'CRITICAL', 'LOGIN_ALERT', 'agent', NULL, NOW() - INTERVAL '10 minutes');

-- Insert test USB activity
INSERT INTO usb_activity (device_id, device_name, connection_type, status, timestamp)
VALUES 
  ('device-001', 'Kingston USB Drive', 'USB 3.0', 'CONNECTED', NOW()),
  ('device-001', 'External HDD', 'USB 2.0', 'DISCONNECTED', NOW() - INTERVAL '2 minutes');

-- Insert test login sessions
INSERT INTO login_sessions (device_id, username, login_time, status)
VALUES 
  ('device-001', 'admin', NOW() - INTERVAL '1 hour', 'ACTIVE'),
  ('device-001', 'guest', NOW() - INTERVAL '3 hours', 'LOGGED_OUT');

-- Insert test files
INSERT INTO files_metadata (device_id, file_name, file_url, storage_path, file_size, file_type)
VALUES 
  ('device-001', 'report.pdf', 'https://supabase.com/storage/v1/object/public/monitor-files/device-001/report.pdf', 'device-001/report.pdf', 1024000, 'pdf'),
  ('device-001', 'data.xlsx', 'https://supabase.com/storage/v1/object/public/monitor-files/device-001/data.xlsx', 'device-001/data.xlsx', 512000, 'xlsx');

-- Insert test system metrics
INSERT INTO system_metrics (device_id, battery_percentage, cpu_usage, ram_usage, system_uptime, timestamp)
VALUES ('device-001', 85, 45.2, 62.1, 3600000, NOW());
```

---

## Step 4: Verify Data in Supabase UI

1. Go to **Table Editor** in Supabase
2. Click on **alerts** table and verify you see test data
3. Click on **usb_activity** table and verify you see test data
4. Click on **login_sessions** table and verify you see test data
5. Click on **files_metadata** table and verify you see test data

---

## Step 5: Check Backend Configuration

### For Java Backend:
1. Verify `/backend/src/main/resources/application.properties` has:
   ```properties
   supabase.url=${SUPABASE_URL}
   supabase.service-role-key=${SUPABASE_SERVICE_ROLE_KEY}
   supabase.enabled=true
   ```

2. Ensure environment variables are set:
   - `SUPABASE_URL=https://oavizsbcurtnsekuwebj.supabase.co`
   - `SUPABASE_SERVICE_ROLE_KEY=your-service-role-key`

### For Node.js Backend:
1. Verify `/backend-node/.env` has:
   ```
   SUPABASE_URL=https://oavizsbcurtnsekuwebj.supabase.co
   SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
   ```

---

## Step 6: Restart Your Backend Services

```bash
# For Java backend
cd backend
mvn clean spring-boot:run

# For Node.js backend  
cd backend-node
npm install
npm start
```

---

## Step 7: Test the Features

1. **Security Alerts**: Navigate to Security Alerts page
   - Should display all test alerts you added
   - Filter by device should work

2. **File Explorer**: Navigate to Files page
   - Should display test files you added
   - Should show file metadata and URLs

3. **Screenshots**: This should continue to work

---

## Troubleshooting

### If security alerts still don't show:

**Check 1**: Verify alerts are in the database
```sql
SELECT COUNT(*) FROM alerts;
SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 10;
```

**Check 2**: Verify the API endpoint is working
```bash
curl "http://localhost:5000/security/alerts"
```

**Check 3**: Check browser console for errors (F12 → Console tab)

---

### If file explorer doesn't show files:

**Check 1**: Verify files are in the database
```sql
SELECT COUNT(*) FROM files_metadata;
SELECT * FROM files_metadata;
```

**Check 2**: Test the file API
```bash
curl "http://localhost:5000/files/all"
```

**Check 3**: Verify Supabase storage bucket is accessible

---

### If RLS is blocking data:

**Quick Fix**: Disable RLS policies temporarily
```sql
ALTER TABLE alerts DISABLE ROW LEVEL SECURITY;
ALTER TABLE usb_activity DISABLE ROW LEVEL SECURITY;
ALTER TABLE security_screenshots DISABLE ROW LEVEL SECURITY;
ALTER TABLE login_sessions DISABLE ROW LEVEL SECURITY;
ALTER TABLE files_metadata DISABLE ROW LEVEL SECURITY;
```

Then re-enable with proper policies for production.

---

## Important Notes

⚠️ **For Production**:
1. Never allow public read/write on sensitive tables
2. Implement proper RLS policies based on user authentication
3. Use environment variables for all credentials
4. Enable Supabase database backups

✅ **What's now included**:
- Complete database schema with all necessary tables
- Proper foreign keys and relationships
- Performance indexes for fast queries
- Support for all features: security alerts, USB monitoring, file explorer, and screenshots

---

## Next Steps

After verifying everything works:
1. Update your Java/Node.js code to use Supabase tables instead of local database for security data
2. Implement data synchronization between your agent and Supabase
3. Set up proper authentication and RLS policies
4. Configure Supabase storage buckets for file uploads
