package com.devin.finops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the FinOps microservice.
 * Replaces the Python backend (data_adapter.py, metrics_calculator.py)
 * with a Spring Boot application exposing WebSocket endpoints for
 * real-time metric delivery to the Angular frontend.
 */
@SpringBootApplication
public class FinOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinOpsApplication.class, args);
    }
}
