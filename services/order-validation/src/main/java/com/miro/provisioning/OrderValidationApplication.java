package com.miro.provisioning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the order-validation microservice used by the Workato
 * Closed-Won provisioning orchestration.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderValidationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderValidationApplication.class, args);
    }
}
