package com.banksphere.employee.service;

import com.banksphere.employee.dto.CustomerLookupResult;
import com.banksphere.employee.dto.CustomerProfileLookupResult;

import java.util.UUID;

public interface CustomerLookupClient {

    CustomerLookupResult lookup(UUID customerId, String bearerToken);

    /** Phase 9C — the Customer 360 aggregation's customer section; the fuller profile. See ADR-008. */
    CustomerProfileLookupResult lookupFullProfile(UUID customerId, String bearerToken);
}
