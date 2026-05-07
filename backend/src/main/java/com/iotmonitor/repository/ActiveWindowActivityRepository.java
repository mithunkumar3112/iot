package com.iotmonitor.repository;

import com.iotmonitor.model.ActiveWindowActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActiveWindowActivityRepository extends JpaRepository<ActiveWindowActivity, Long> {

    List<ActiveWindowActivity> findTop100ByOrderByTimestampDesc();

    List<ActiveWindowActivity> findByDeviceIdOrderByTimestampDesc(String deviceId);

    List<ActiveWindowActivity> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);

    @Query("SELECT a FROM ActiveWindowActivity a WHERE a.deviceId = :deviceId AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<ActiveWindowActivity> findByDeviceIdAndTimestampRange(
            @Param("deviceId") String deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );

    @Query("SELECT a FROM ActiveWindowActivity a WHERE (:deviceId IS NULL OR a.deviceId = :deviceId) AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<ActiveWindowActivity> findTimeline(
            @Param("deviceId") String deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );

    @Query("SELECT DISTINCT a.deviceId FROM ActiveWindowActivity a ORDER BY a.deviceId")
    List<String> findDistinctDeviceIds();
}
