package com.iotmonitor.repository;

import com.iotmonitor.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findByDeviceId(String deviceId);
    List<FileMetadata> findTop10ByOrderByUploadTimeDesc();
}