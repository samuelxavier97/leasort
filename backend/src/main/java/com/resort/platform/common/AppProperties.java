package com.resort.platform.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(BootstrapAdmin bootstrapAdmin) {

    public record BootstrapAdmin(String email, String password) {}
}
