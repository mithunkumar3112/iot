package com.iotmonitor.repository;

import com.iotmonitor.model.ProcessData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProcessDataRepository extends JpaRepository<ProcessData, Long> {

    List<ProcessData> findByDeviceIdOrderByTimestampDesc(String deviceId);

    @Query("SELECT p FROM ProcessData p WHERE p.deviceId = :deviceId AND p.cpuUsage > :cpuThreshold ORDER BY p.timestamp DESC")
    List<ProcessData> findHighCpuProcesses(@Param("deviceId") String deviceId, @Param("cpuThreshold") double cpuThreshold);

    @Query("SELECT DISTINCT p.deviceId FROM ProcessData p ORDER BY p.deviceId")
    List<String> findAllUniqueDeviceIds();
}