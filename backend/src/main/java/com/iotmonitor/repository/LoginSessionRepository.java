package com.iotmonitor.repository;

import com.iotmonitor.model.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    List<LoginSession> findTop200ByOrderByLoginTimeDesc();
    List<LoginSession> findTop200ByDeviceIdOrderByLoginTimeDesc(String deviceId);
}
