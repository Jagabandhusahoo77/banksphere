package com.banksphere.account.service;

import com.banksphere.account.dto.TransactionRecordRequest;
import com.banksphere.account.dto.TransactionRecordResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestTransactionClient implements TransactionClient {

    private final RestClient transactionServiceRestClient;

    @Override
    public Optional<TransactionRecordResult> recordTransaction(TransactionRecordRequest request, String bearerToken) {
        try {
            TransactionRecordResult result = transactionServiceRestClient.post()
                    .uri("/api/v1/transactions")
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .body(request)
                    .retrieve()
                    .body(TransactionRecordResult.class);
            return Optional.ofNullable(result);
        } catch (RestClientException ex) {
            // Deliberately never rethrown — see TransactionClient's javadoc.
            // Not the token's fault if this fails; not logging the token itself.
            log.error("Failed to record transaction for account {} (type={}, amount={}): {}",
                    request.accountId(), request.transactionType(), request.amount(), ex.getMessage());
            return Optional.empty();
        }
    }
}
