package com.iotmonitor.repository;

import com.iotmonitor.model.AppActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface AppActivityRepository extends JpaRepository<AppActivity, Long> {

    // Get all app activities for a device, ordered by timestamp (newest first)
    List<AppActivity> findByDeviceIdOrderByTimestampDesc(String deviceId);

    // Get app activities for a device within a time range
    @Query("SELECT a FROM AppActivity a WHERE a.deviceId = :deviceId AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AppActivity> findByDeviceIdAndTimestampRange(
            @Param("deviceId") String deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // Get opened/closed activities for specific app
    @Query("SELECT a FROM AppActivity a WHERE a.deviceId = :deviceId AND a.appName = :appName AND a.status = :status ORDER BY a.timestamp DESC")
    List<AppActivity> findByDeviceIdAndAppNameAndStatus(
            @Param("deviceId") String deviceId,
            @Param("appName") String appName,
            @Param("status") String status
    );

    // Get activities for a specific status (OPENED or CLOSED)
    List<AppActivity> findByDeviceIdAndStatusOrderByTimestampDesc(String deviceId, String status);

    // Search for app activity by name pattern
    @Query("SELECT a FROM AppActivity a WHERE a.deviceId = :deviceId AND LOWER(a.appName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY a.timestamp DESC")
    List<AppActivity> searchByAppName(
            @Param("deviceId") String deviceId,
            @Param("searchTerm") String searchTerm
    );

    @Query("SELECT DISTINCT a.deviceId FROM AppActivity a ORDER BY a.deviceId")
    List<String> findDistinctDeviceIds();
}

