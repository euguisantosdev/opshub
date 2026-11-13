package dev.opshub.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Relógio injetável: os testes substituem este bean para controlar o tempo do SLA. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
