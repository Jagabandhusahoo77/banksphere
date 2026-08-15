package com.banksphere.employee.service;

import com.banksphere.employee.dto.RemotePage;
import com.banksphere.employee.dto.TransactionLookupResult;
import com.banksphere.employee.exception.DownstreamOperationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class RestTransactionLookupClient implements TransactionLookupClient {

    private final RestClient transactionServiceRestClient;

    public RestTransactionLookupClient(@Qualifier("transactionServiceRestClient") RestClient transactionServiceRestClient) {
        this.transactionServiceRestClient = transactionServiceRestClient;
    }

    @Override
    public List<TransactionLookupResult> recentTransactionsForAccount(UUID accountId, String bearerToken) {
        RemotePage<TransactionLookupResult> page = call(() -> transactionServiceRestClient.get()
                .uri("/api/v1/transactions/employee/account/{accountId}", accountId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .body(new ParameterizedTypeReference<RemotePage<TransactionLookupResult>>() {}));
        return page != null ? page.content() : List.of();
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
