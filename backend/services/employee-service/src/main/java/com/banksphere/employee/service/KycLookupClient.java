package com.banksphere.employee.service;

import com.banksphere.employee.dto.KycApplicationLookupResult;

import java.util.Optional;
import java.util.UUID;

/** Calls kyc-service's Customer 360 employee endpoint, forwarding the acting employee's own bearer token. See ADR-008. */
public interface KycLookupClient {

    /** Empty when the customer has never started KYC — a normal state, not an error. */
    Optional<KycApplicationLookupResult> lookupByCustomerId(UUID customerId, String bearerToken);
}
