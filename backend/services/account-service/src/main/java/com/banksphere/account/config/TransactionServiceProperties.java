package com.banksphere.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "banksphere.transaction-service")
public record TransactionServiceProperties(String baseUrl) {
}
