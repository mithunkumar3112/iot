package com.iotmonitor.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(scanBasePackages = {"com.iotmonitor"})
@EnableScheduling
public class Laptopagentapplication {

    public static void main(String[] args) {
        SpringApplication.run(Laptopagentapplication.class, args);
    }
}