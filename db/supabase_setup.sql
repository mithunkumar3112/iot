-- RUN THIS IN SUPABASE SQL EDITOR

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ===================================
-- CORE TABLES
-- ===================================

-- Table for users
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Table for devices
CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    mac_address VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    is_online BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ===================================
-- SECURITY & MONITORING TABLES
-- ===================================

-- Table for security alerts
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    device_name VARCHAR(255),
    message TEXT,
    severity VARCHAR(20),
    type VARCHAR(50) NOT NULL,
    source VARCHAR(100),
    process_name VARCHAR(255),
    screenshot_url VARCHAR(500),
    file_path VARCHAR(500),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- Table for USB activity
CREATE TABLE IF NOT EXISTS usb_activity (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    device_name VARCHAR(180),
    connection_type VARCHAR(60),
    status VARCHAR(30),
    screenshot_url VARCHAR(500),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- Table for security screenshots
CREATE TABLE IF NOT EXISTS security_screenshots (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    alert_id BIGINT,
    usb_activity_id BIGINT,
    event_type VARCHAR(60) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    url VARCHAR(500),
    file_size BIGINT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    FOREIGN KEY (alert_id) REFERENCES alerts(id) ON DELETE CASCADE,
    FOREIGN KEY (usb_activity_id) REFERENCES usb_activity(id) ON DELETE CASCADE
);

-- Table for login sessions
CREATE TABLE IF NOT EXISTS login_sessions (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    username VARCHAR(160),
    login_time TIMESTAMP WITH TIME ZONE NOT NULL,
    logout_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- ===================================
-- FILE & STORAGE TABLES
-- ===================================

-- Table for file metadata
CREATE TABLE IF NOT EXISTS files_metadata (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_url TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    file_size BIGINT,
    file_type VARCHAR(50),
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- ===================================
-- PROCESS & ANALYTICS TABLES
-- ===================================

-- Table for process logs
CREATE TABLE IF NOT EXISTS process_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT NOT NULL,
    process_name TEXT NOT NULL,
    cpu_usage DOUBLE PRECISION NOT NULL,
    memory_usage DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- Table for app detection alerts
CREATE TABLE IF NOT EXISTS app_detections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT NOT NULL,
    app_name TEXT NOT NULL,
    message TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- Table for system metrics
CREATE TABLE IF NOT EXISTS system_metrics (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    battery_percentage DOUBLE PRECISION,
    is_charging BOOLEAN DEFAULT FALSE,
    cpu_usage DOUBLE PRECISION,
    ram_usage DOUBLE PRECISION,
    system_uptime BIGINT,
    last_active_app VARCHAR(255),
    running_apps_json TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- Table for usage analytics
CREATE TABLE IF NOT EXISTS usage_analytics (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    app_name VARCHAR(255),
    total_usage BIGINT,
    usage_date DATE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    UNIQUE(device_id, app_name, usage_date)
);

-- Table for active window activity
CREATE TABLE IF NOT EXISTS active_window_activity (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    active_window VARCHAR(255),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- Table for commands
CREATE TABLE IF NOT EXISTS commands (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    parameters TEXT,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- ===================================
-- SCREENSHOTS TABLE
-- ===================================

-- Table for screenshots
CREATE TABLE IF NOT EXISTS screenshots (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    file_size BIGINT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

-- ===================================
-- INDEXES FOR PERFORMANCE
-- ===================================

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_alerts_device_id ON alerts(device_id);
CREATE INDEX IF NOT EXISTS idx_alerts_timestamp ON alerts(timestamp);
CREATE INDEX IF NOT EXISTS idx_usb_activity_device_id ON usb_activity(device_id);
CREATE INDEX IF NOT EXISTS idx_usb_activity_timestamp ON usb_activity(timestamp);
CREATE INDEX IF NOT EXISTS idx_security_screenshots_device_id ON security_screenshots(device_id);
CREATE INDEX IF NOT EXISTS idx_login_sessions_device_id ON login_sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_files_metadata_device_id ON files_metadata(device_id);
CREATE INDEX IF NOT EXISTS idx_process_logs_device_id ON process_logs(device_id);
CREATE INDEX IF NOT EXISTS idx_process_logs_timestamp ON process_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_app_detections_device_id ON app_detections(device_id);
CREATE INDEX IF NOT EXISTS idx_system_metrics_device_id ON system_metrics(device_id);
CREATE INDEX IF NOT EXISTS idx_usage_analytics_device_id ON usage_analytics(device_id);
CREATE INDEX IF NOT EXISTS idx_active_window_device_id ON active_window_activity(device_id);
CREATE INDEX IF NOT EXISTS idx_screenshots_device_id ON screenshots(device_id);

-- ===================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ===================================

-- Enable RLS
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE usb_activity ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_screenshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE login_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE files_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE process_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_detections ENABLE ROW LEVEL SECURITY;
ALTER TABLE system_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_analytics ENABLE ROW LEVEL SECURITY;
ALTER TABLE active_window_activity ENABLE ROW LEVEL SECURITY;
ALTER TABLE commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE screenshots ENABLE ROW LEVEL SECURITY;

-- Note: Configure RLS policies based on your authentication setup in Supabase
-- For now, you can temporarily disable by commenting out the ALTER TABLE statements above
-- or set public policies for development testing
