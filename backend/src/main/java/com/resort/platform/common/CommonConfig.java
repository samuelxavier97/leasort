package com.resort.platform.common;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class CommonConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Fonte de aleatoriedade do código de convite (RN07, D-082); substituída nos testes. */
    @Bean
    RandomGenerator secureRandom() {
        return new SecureRandom();
    }
}
