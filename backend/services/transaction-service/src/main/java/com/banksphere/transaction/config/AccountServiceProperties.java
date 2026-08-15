package com.banksphere.transaction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "banksphere.account-service")
public record AccountServiceProperties(String baseUrl) {
}
