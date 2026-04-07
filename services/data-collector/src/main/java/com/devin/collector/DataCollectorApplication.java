package com.devin.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Data Collector microservice entry point.
 * Polls Devin API endpoints at configurable intervals and publishes
 * results to Redis for consumption by the websocket-service.
 */
@SpringBootApplication(scanBasePackages = {"com.devin.collector", "com.devin.common"})
public class DataCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataCollectorApplication.class, args);
    }
}
