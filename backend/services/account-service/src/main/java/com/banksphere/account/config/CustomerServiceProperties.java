package com.banksphere.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Phase 9D — used only for the step-up confirmation call; see {@code StepUpVerificationClient}. */
@ConfigurationProperties(prefix = "banksphere.customer-service")
public record CustomerServiceProperties(String baseUrl) {
}
