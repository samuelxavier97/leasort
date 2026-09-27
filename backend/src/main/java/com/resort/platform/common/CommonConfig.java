package com.resort.platform.common;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class CommonConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * {@code @Past}/{@code @PastOrPresent} dos DTOs (nascimento de Lead e acompanhante) comparam com o
     * "hoje" da operação ({@code APP_TIMEZONE}), não com o fuso da JVM ou do container.
     */
    @Bean
    ValidationConfigurationCustomizer operationClockForValidation(Clock clock, AppProperties properties) {
        ZoneId zone = ZoneId.of(properties.timezone());
        return configuration -> configuration.clockProvider(() -> clock.withZone(zone));
    }

    /** Fonte de aleatoriedade do código de convite (RN07, D-082); substituída nos testes. */
    @Bean
    RandomGenerator secureRandom() {
        return new SecureRandom();
    }
}
