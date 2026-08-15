package com.banksphere.employee.service;

import com.banksphere.employee.dto.AccountLookupResult;
import com.banksphere.employee.dto.EmployeeDepositResult;
import com.banksphere.employee.exception.DownstreamOperationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class RestAccountOperationsClient implements AccountOperationsClient {

    private final RestClient accountServiceRestClient;

    public RestAccountOperationsClient(@Qualifier("accountServiceRestClient") RestClient accountServiceRestClient) {
        this.accountServiceRestClient = accountServiceRestClient;
    }

    @Override
    public AccountLookupResult lookupByAccountNumber(String accountNumber, String bearerToken) {
        return call(() -> accountServiceRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/employee-lookup")
                        .queryParam("accountNumber", accountNumber).build())
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .body(AccountLookupResult.class));
    }

    @Override
    public List<AccountLookupResult> lookupByCustomerId(UUID customerId, String bearerToken) {
        return call(() -> accountServiceRestClient.get()
                .uri("/api/v1/accounts/employee-lookup/customer/{customerId}", customerId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccountLookupResult>>() {}));
    }

    @Override
    public EmployeeDepositResult deposit(UUID accountId, BigDecimal amount, String description, String bearerToken) {
        return call(() -> accountServiceRestClient.post()
                .uri("/api/v1/accounts/{id}/employee-deposit", accountId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .body(Map.of("amount", amount, "description", description == null ? "" : description))
                .retrieve()
                .body(EmployeeDepositResult.class));
    }

    private <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException ex) {
            throw new DownstreamOperationException(ex.getStatusCode().value(), extractMessage(ex));
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMessage(RestClientResponseException ex) {
        try {
            Map<String, Object> body = ex.getResponseBodyAs(Map.class);
            Object message = body != null ? body.get("message") : null;
            return message != null ? message.toString() : ex.getMessage();
        } catch (Exception parseFailure) {
            return ex.getMessage();
        }
    }
}
