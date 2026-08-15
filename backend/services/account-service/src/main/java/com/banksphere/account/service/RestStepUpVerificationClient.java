package com.banksphere.account.service;

import com.banksphere.account.dto.StepUpConfirmRequest;
import com.banksphere.account.dto.StepUpConfirmResponse;
import com.banksphere.account.exception.StepUpVerificationFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RestStepUpVerificationClient implements StepUpVerificationClient {

    private final RestClient customerServiceRestClient;

    @Override
    public void confirmTransferStepUp(UUID challengeId, UUID sourceAccountId, String destinationAccountNumber,
                                       String destinationIfsc, BigDecimal amount, String currency, String bearerToken) {
        StepUpConfirmRequest request = new StepUpConfirmRequest(
                challengeId, "STEP_UP_TRANSFER",
                new StepUpConfirmRequest.TransferContext(sourceAccountId, destinationAccountNumber, destinationIfsc, amount, currency));

        try {
            StepUpConfirmResponse response = customerServiceRestClient.post()
                    .uri("/api/v1/auth/step-up/confirm")
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .body(request)
                    .retrieve()
                    .body(StepUpConfirmResponse.class);

            if (response == null || !response.confirmed()) {
                throw new StepUpVerificationFailedException("Step-up authorization could not be confirmed");
            }
        } catch (RestClientResponseException ex) {
            throw new StepUpVerificationFailedException(extractMessage(ex));
        } catch (RestClientException ex) {
            // Unreachable/timed-out customer-service must never be
            // silently treated as "step-up satisfied" — see this
            // interface's own javadoc.
            throw new StepUpVerificationFailedException("Could not verify step-up authorization");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMessage(RestClientResponseException ex) {
        try {
            Map<String, Object> body = ex.getResponseBodyAs(Map.class);
            Object message = body != null ? body.get("message") : null;
            return message != null ? message.toString() : "Step-up authorization could not be confirmed";
        } catch (Exception parseFailure) {
            return "Step-up authorization could not be confirmed";
        }
    }
}
