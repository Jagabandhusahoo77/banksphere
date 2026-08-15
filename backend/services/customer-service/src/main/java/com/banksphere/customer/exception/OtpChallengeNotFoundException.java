package com.banksphere.customer.exception;

/**
 * Thrown when a {@code challengeId} does not correspond to any real
 * challenge at all — distinct from {@link InvalidOtpException} (a real
 * challenge that failed verification for some reason). Safe to surface
 * as a distinct {@code 404}: {@code challengeId} is a server-generated
 * random UUID the client only ever learns from a prior request/step-up
 * response, so confirming "no such challenge" leaks nothing about any
 * customer or identifier.
 */
public class OtpChallengeNotFoundException extends RuntimeException {

    public OtpChallengeNotFoundException() {
        super("OTP challenge not found");
    }
}
