package com.resort.platform;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Substitui o Clock da aplicação nos testes de integração (D-074). */
    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    /** Substitui a aleatoriedade do código de convite nos testes de integração (D-082). */
    @Bean
    @Primary
    ScriptedRandom scriptedRandom() {
        return new ScriptedRandom();
    }

    /** Envolve o DataSource para contar statements (D-098, teste de consultas fixas do dashboard). */
    @Bean
    static BeanPostProcessor statementCounting() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource dataSource && !(bean instanceof StatementCounter)
                        ? new StatementCounter(dataSource)
                        : bean;
            }
        };
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
}
