package com.banksphere.kyc.repository;

import com.banksphere.kyc.entity.KycApplication;
import com.banksphere.kyc.entity.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycApplicationRepository extends JpaRepository<KycApplication, UUID> {

    /**
     * "My KYC status" — a customer has at most one non-terminal
     * application at a time (enforced by the V1 migration's partial
     * unique index), so this covers both "find my current application"
     * and the create-time duplicate-draft check.
     */
    Optional<KycApplication> findFirstByCustomerIdAndStatusNotInOrderByCreatedAtDesc(
            UUID customerId, List<KycStatus> terminalStatuses);

    List<KycApplication> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("SELECT a FROM KycApplication a WHERE (:status IS NULL OR a.status = :status) ORDER BY a.submittedAt ASC NULLS LAST")
    List<KycApplication> findQueue(@Param("status") KycStatus status);
}
