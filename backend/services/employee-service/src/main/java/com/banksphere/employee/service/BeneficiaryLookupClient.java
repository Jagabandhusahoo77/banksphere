package com.banksphere.employee.service;

import com.banksphere.employee.dto.BeneficiaryLookupResult;

import java.util.List;
import java.util.UUID;

/** Calls beneficiary-service's Customer 360 employee endpoint, forwarding the acting employee's own bearer token. See ADR-008. */
public interface BeneficiaryLookupClient {

    List<BeneficiaryLookupResult> lookupByCustomerId(UUID customerId, String bearerToken);
}
