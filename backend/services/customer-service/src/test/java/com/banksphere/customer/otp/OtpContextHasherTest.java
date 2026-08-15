package com.banksphere.customer.otp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The anti-tampering primitive underneath Phase 9D's step-up
 * operation-binding — see ADR-009. Deterministic, order-sensitive, and
 * sensitive to every field, since a real transfer's context includes all
 * five canonical parts.
 */
class OtpContextHasherTest {

    private final OtpContextHasher hasher = new OtpContextHasher();

    @Test
    void hash_isDeterministic_forIdenticalInput() {
        List<String> parts = List.of("acc-1", "222222222222", "BANK0000001", "1000.00", "INR");

        assertThat(hasher.hash(parts)).isEqualTo(hasher.hash(parts));
    }

    @Test
    void hash_changesWhenAmountChanges() {
        List<String> original = List.of("acc-1", "222222222222", "BANK0000001", "1000.00", "INR");
        List<String> tampered = List.of("acc-1", "222222222222", "BANK0000001", "100000.00", "INR");

        assertThat(hasher.hash(original)).isNotEqualTo(hasher.hash(tampered));
    }

    @Test
    void hash_changesWhenRecipientChanges() {
        List<String> original = List.of("acc-1", "222222222222", "BANK0000001", "1000.00", "INR");
        List<String> tampered = List.of("acc-1", "999999999999", "BANK0000001", "1000.00", "INR");

        assertThat(hasher.hash(original)).isNotEqualTo(hasher.hash(tampered));
    }

    @Test
    void hash_changesWhenSourceAccountChanges() {
        List<String> original = List.of("acc-1", "222222222222", "BANK0000001", "1000.00", "INR");
        List<String> tampered = List.of("acc-2", "222222222222", "BANK0000001", "1000.00", "INR");

        assertThat(hasher.hash(original)).isNotEqualTo(hasher.hash(tampered));
    }

    @Test
    void hash_isNotVulnerableToSimpleDelimiterConcatenationCollisions() {
        // Without a real delimiter, "ab"+"c" and "a"+"bc" would hash
        // identically under naive string concatenation. The ASCII unit
        // separator between fields prevents that class of collision.
        List<String> a = List.of("ab", "c");
        List<String> b = List.of("a", "bc");

        assertThat(hasher.hash(a)).isNotEqualTo(hasher.hash(b));
    }

    @Test
    void hash_isA64CharacterHexSha256Digest() {
        String digest = hasher.hash(List.of("x"));

        assertThat(digest).hasSize(64).matches("^[0-9a-f]{64}$");
    }
}
