package com.pcom.monitoring;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot Admin: discovers every service through Eureka and shows health, metrics, loggers, threads. */
@SpringBootApplication
@EnableAdminServer
public class MonitoringServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonitoringServiceApplication.class, args);
    }
}
