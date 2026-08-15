package com.banksphere.kyc.repository;

import com.banksphere.kyc.entity.DocumentStatus;
import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByKycApplicationIdOrderBySubmittedAtAsc(UUID kycApplicationId);

    /**
     * The duplicate-upload guard: a customer may re-upload a document
     * type only once its prior document(s) of that type are all
     * REJECTED — see V2 migration's comment for why this isn't a DB
     * constraint. Excludes REJECTED so re-upload after rejection is
     * always allowed.
     */
    boolean existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(
            UUID kycApplicationId, DocumentType documentType, DocumentStatus excludedStatus);
}
