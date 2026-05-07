package com.iotmonitor.repository;

import com.iotmonitor.model.ClipboardEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ClipboardEntryRepository extends JpaRepository<ClipboardEntry, Long> {

    List<ClipboardEntry> findTop1ByDeviceIdOrderByTimestampDesc(String deviceId);

    List<ClipboardEntry> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);

    @Query("SELECT DISTINCT c.deviceId FROM ClipboardEntry c ORDER BY c.deviceId")
    List<String> findDistinctDeviceIds();

    @Query("SELECT c FROM ClipboardEntry c WHERE c.deviceId = :deviceId AND c.timestamp BETWEEN :startTime AND :endTime ORDER BY c.timestamp DESC")
    List<ClipboardEntry> findByDeviceIdAndTimestampRange(
            @Param("deviceId") String deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
}
