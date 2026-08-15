package com.banksphere.kyc.storage;

import java.util.UUID;

/**
 * The KYC domain's only dependency for persisting/retrieving uploaded
 * document bytes — deliberately narrow (store/load, keyed by an opaque
 * String reference) so the domain and service layers never depend on
 * AWS SDK classes or any other storage-provider type directly. Today's
 * only implementation, {@link LocalDocumentStorage}, writes to the local
 * filesystem for Docker Compose development. A future {@code
 * S3DocumentStorage} implementing this same interface is a drop-in
 * replacement — no KYC business logic changes. See ADR-008.
 *
 * <p>The returned {@code storageReference} is an opaque key, never a
 * public URL — {@code KycDocument.storageReference} stores it verbatim
 * and it is never serialized into a customer- or employee-facing DTO
 * (see the {@code dto} package's document response types).
 */
public interface DocumentStorage {

    String store(UUID kycApplicationId, String originalFileName, byte[] content);

    byte[] load(String storageReference);
}
