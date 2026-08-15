package com.banksphere.employee.service;

import com.banksphere.employee.dto.BeneficiaryLookupResult;
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
public class RestBeneficiaryLookupClient implements BeneficiaryLookupClient {

    private final RestClient beneficiaryServiceRestClient;

    public RestBeneficiaryLookupClient(@Qualifier("beneficiaryServiceRestClient") RestClient beneficiaryServiceRestClient) {
        this.beneficiaryServiceRestClient = beneficiaryServiceRestClient;
    }

    @Override
    public List<BeneficiaryLookupResult> lookupByCustomerId(UUID customerId, String bearerToken) {
        return call(() -> beneficiaryServiceRestClient.get()
                .uri("/api/v1/beneficiaries/employee/customer/{customerId}", customerId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<BeneficiaryLookupResult>>() {}));
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
