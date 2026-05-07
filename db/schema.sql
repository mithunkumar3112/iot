CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE devices (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    mac_address VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    last_seen_at TIMESTAMP,
    is_online BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE system_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    battery_percentage DOUBLE NOT NULL,
    is_charging BOOLEAN NOT NULL,
    cpu_usage DOUBLE NOT NULL,
    ram_usage DOUBLE NOT NULL,
    system_uptime BIGINT NOT NULL,
    last_active_app VARCHAR(255),
    running_apps_json TEXT,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE TABLE screenshots (
    id VARCHAR(36) PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE TABLE commands (
    id VARCHAR(36) PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    parameters TEXT,
    issued_at TIMESTAMP NOT NULL,
    executed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE TABLE usage_analytics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(100) NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    total_usage BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_usage_device_app_date (device_id, app_name, usage_date),
    INDEX idx_usage_device_date (device_id, usage_date),
    INDEX idx_usage_date (usage_date)
);

CREATE TABLE active_window_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(100) NOT NULL,
    active_window VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    INDEX idx_active_window_device_time (device_id, timestamp),
    INDEX idx_active_window_time (timestamp)
);

CREATE TABLE usb_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(120) NOT NULL,
    device_name VARCHAR(180) NOT NULL,
    connection_type VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL,
    screenshot_url VARCHAR(500),
    timestamp TIMESTAMP NOT NULL,
    INDEX idx_usb_device_time (device_id, timestamp),
    INDEX idx_usb_time (timestamp)
);

CREATE TABLE login_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(120) NOT NULL,
    username VARCHAR(160) NOT NULL,
    login_time TIMESTAMP NOT NULL,
    logout_time TIMESTAMP NULL,
    status VARCHAR(40) NOT NULL,
    INDEX idx_session_device_time (device_id, login_time),
    INDEX idx_session_status (status)
);

CREATE TABLE security_screenshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(120) NOT NULL,
    alert_id BIGINT NULL,
    usb_activity_id BIGINT NULL,
    event_type VARCHAR(60) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    url VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    INDEX idx_security_screenshot_device_time (device_id, timestamp)
);

-- Existing JPA-managed alerts table is extended by Hibernate ddl-auto=update.
-- For manual databases, add:
-- ALTER TABLE alerts ADD COLUMN process_name VARCHAR(160);
