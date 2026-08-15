package com.banksphere.customer.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * {@link SecureRandom}, never {@link java.util.Random}/a UUID
 * substring/a timestamp/anything derived from customer or account data —
 * see ADR-009. Generates a zero-padded numeric string of {@code length}
 * digits (e.g. {@code "003914"}), never converts to/from an {@code int}
 * (which would silently drop leading zeros).
 */
@Component
public class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
