package dev.opshub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades da aplicação (prefixo {@code app}). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, Bootstrap bootstrap, Monitoring monitoring, Demo demo) {

    public record Security(String jwtSecret, int jwtTtlMinutes) {
    }

    public record Bootstrap(String adminUsername, String adminPassword) {
    }

    public record Monitoring(boolean enabled, long intervalMs) {
    }

    public record Demo(String password) {
    }
}
