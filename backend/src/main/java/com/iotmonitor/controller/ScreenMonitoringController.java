package com.iotmonitor.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/screen")
public class ScreenMonitoringController {

    @GetMapping("/status")
    public String status() {
        return "Screen monitoring active";
    }
}
