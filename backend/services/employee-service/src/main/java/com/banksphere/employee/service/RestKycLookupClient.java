package com.banksphere.employee.service;

import com.banksphere.employee.dto.KycApplicationLookupResult;
import com.banksphere.employee.exception.DownstreamOperationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class RestKycLookupClient implements KycLookupClient {

    private final RestClient kycServiceRestClient;

    public RestKycLookupClient(@Qualifier("kycServiceRestClient") RestClient kycServiceRestClient) {
        this.kycServiceRestClient = kycServiceRestClient;
    }

    @Override
    public Optional<KycApplicationLookupResult> lookupByCustomerId(UUID customerId, String bearerToken) {
        // kyc-service returns 204 No Content (not 404) when the customer
        // has never started KYC — RestClient's retrieve().body(...)
        // returns null for an empty 2xx body, so no exception is thrown
        // for this normal, common case.
        KycApplicationLookupResult result = call(() -> kycServiceRestClient.get()
                .uri("/api/v1/kyc/employee/customer/{customerId}", customerId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .body(KycApplicationLookupResult.class));
        return Optional.ofNullable(result);
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
