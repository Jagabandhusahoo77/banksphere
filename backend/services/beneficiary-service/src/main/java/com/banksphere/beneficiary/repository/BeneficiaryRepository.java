package com.banksphere.beneficiary.repository;

import com.banksphere.beneficiary.entity.Beneficiary;
import com.banksphere.beneficiary.entity.BeneficiaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    List<Beneficiary> findByCustomerIdAndStatus(UUID customerId, BeneficiaryStatus status);

    /** The application-layer half of duplicate prevention — see the V1 migration's partial unique index for the DB-level half. */
    boolean existsByCustomerIdAndAccountNumberAndIfscAndStatus(
            UUID customerId, String accountNumber, String ifsc, BeneficiaryStatus status);
}
