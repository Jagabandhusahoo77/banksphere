package com.banksphere.employee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 9C adds {@code transactionServiceBaseUrl}/{@code
 * beneficiaryServiceBaseUrl}/{@code kycServiceBaseUrl} for the Customer
 * 360 aggregation — same forwarded-bearer-token pattern as the two
 * Phase 9B fields. See ADR-008.
 */
@ConfigurationProperties(prefix = "banksphere.downstream")
public record DownstreamServiceProperties(
        String accountServiceBaseUrl,
        String customerServiceBaseUrl,
        String transactionServiceBaseUrl,
        String beneficiaryServiceBaseUrl,
        String kycServiceBaseUrl
) {
}
