package com.banksphere.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestAccountOwnershipClient implements AccountOwnershipClient {

    private final RestClient accountServiceRestClient;

    @Override
    public boolean isOwnedByCaller(UUID accountId, String bearerToken) {
        try {
            accountServiceRestClient.get()
                    .uri("/api/v1/accounts/{id}", accountId)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            // account-service's own 403 (not the owner) or 404 (no such
            // account) both mean "not verified as owned" — same outcome.
            return false;
        } catch (RestClientException ex) {
            // Network error, timeout, or account-service 5xx: fail closed
            // rather than assume ownership because we couldn't check.
            log.error("Failed to verify ownership of account {}: {}", accountId, ex.getMessage());
            return false;
        }
    }
}
