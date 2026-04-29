package com.iotmonitor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * File information DTO for file listing API
 */
public class FileInfo {
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("size")
    private long size;
    
    @JsonProperty("isDirectory")
    private boolean isDirectory;
    
    @JsonProperty("lastModified")
    private long lastModified;

    public FileInfo(String name, String path, long size, boolean isDirectory, long lastModified) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.isDirectory = isDirectory;
        this.lastModified = lastModified;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
}
