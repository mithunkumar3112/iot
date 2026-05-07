package com.iotmonitor.repository;

import com.iotmonitor.model.SecurityScreenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SecurityScreenshotRepository extends JpaRepository<SecurityScreenshot, Long> {
    List<SecurityScreenshot> findByAlertIdOrderByTimestampDesc(Long alertId);
    List<SecurityScreenshot> findByUsbActivityIdOrderByTimestampDesc(Long usbActivityId);
}
