package com.miro.provisioning.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe security configuration loaded from {@code app.security}.
 * Validation makes the application fail during startup when the API key is
 * missing instead of accepting traffic with an invalid configuration.
 * The record keeps secret handling centralized and immutable after binding.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(@NotBlank String apiKey) {
}
