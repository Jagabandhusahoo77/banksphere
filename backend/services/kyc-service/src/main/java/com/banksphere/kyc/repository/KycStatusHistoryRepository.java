package com.banksphere.kyc.repository;

import com.banksphere.kyc.entity.KycStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycStatusHistoryRepository extends JpaRepository<KycStatusHistory, UUID> {

    List<KycStatusHistory> findByKycApplicationIdOrderByChangedAtAsc(UUID kycApplicationId);
}
