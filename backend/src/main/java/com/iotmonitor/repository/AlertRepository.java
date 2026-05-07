package com.iotmonitor.repository;

import com.iotmonitor.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByDeviceIdOrderByTimestampDesc(String deviceId);

    List<Alert> findByAcknowledgedFalseOrderByTimestampDesc();

    List<Alert> findTop100ByOrderByTimestampDesc();

    List<Alert> findTop100ByDeviceIdOrderByTimestampDesc(String deviceId);
}
