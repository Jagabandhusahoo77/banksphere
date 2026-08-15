package com.banksphere.employee.service;

import com.banksphere.employee.dto.CustomerLookupResult;
import com.banksphere.employee.dto.CustomerProfileLookupResult;
import com.banksphere.employee.exception.DownstreamOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class RestCustomerLookupClient implements CustomerLookupClient {

    private final RestClient customerServiceRestClient;

    public RestCustomerLookupClient(@Qualifier("customerServiceRestClient") RestClient customerServiceRestClient) {
        this.customerServiceRestClient = customerServiceRestClient;
    }

    @Override
    public CustomerLookupResult lookup(UUID customerId, String bearerToken) {
        try {
            return customerServiceRestClient.get()
                    .uri("/api/v1/customers/employee-lookup/{id}", customerId)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .body(CustomerLookupResult.class);
        } catch (RestClientResponseException ex) {
            throw new DownstreamOperationException(ex.getStatusCode().value(), extractMessage(ex));
        }
    }

    @Override
    public CustomerProfileLookupResult lookupFullProfile(UUID customerId, String bearerToken) {
        try {
            return customerServiceRestClient.get()
                    .uri("/api/v1/customers/employee-lookup/{id}/profile", customerId)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .body(CustomerProfileLookupResult.class);
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
