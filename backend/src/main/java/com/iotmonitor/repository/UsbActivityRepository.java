package com.iotmonitor.repository;

import com.iotmonitor.model.UsbActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UsbActivityRepository extends JpaRepository<UsbActivity, Long> {
    List<UsbActivity> findTop100ByOrderByTimestampDesc();
    List<UsbActivity> findTop100ByDeviceIdOrderByTimestampDesc(String deviceId);
}
