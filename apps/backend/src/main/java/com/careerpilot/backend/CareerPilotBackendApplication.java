package com.careerpilot.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareerPilotBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(CareerPilotBackendApplication.class, args);
    }
}
