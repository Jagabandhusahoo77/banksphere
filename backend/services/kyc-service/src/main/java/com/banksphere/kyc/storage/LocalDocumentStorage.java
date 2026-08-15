package com.banksphere.kyc.storage;

import com.banksphere.kyc.exception.DocumentStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Filesystem-backed {@link DocumentStorage} for local development —
 * writes under {@code banksphere.kyc.document-storage-path} (default
 * {@code /data/kyc-documents}, pre-created with correct ownership in the
 * Dockerfile). Do NOT introduce AWS/S3 yet — see ADR-008's "future S3
 * migration" section for how this gets swapped later without touching
 * KYC business logic.
 *
 * <p>The stored filename is always a freshly generated UUID, never the
 * caller-supplied {@code originalFileName} — that avoids both path
 * traversal via a crafted filename and same-name collisions between
 * unrelated uploads. {@code originalFileName} is preserved only as
 * metadata on {@code KycDocument}, never used to address the file on
 * disk.
 */
@Component
public class LocalDocumentStorage implements DocumentStorage {

    private final Path basePath;

    public LocalDocumentStorage(@Value("${banksphere.kyc.document-storage-path}") String documentStoragePath) {
        this.basePath = Path.of(documentStoragePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create KYC document storage directory: " + basePath, ex);
        }
    }

    @Override
    public String store(UUID kycApplicationId, String originalFileName, byte[] content) {
        String storageReference = kycApplicationId + "/" + UUID.randomUUID() + extensionOf(originalFileName);
        Path target = resolveWithinBasePath(storageReference);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new DocumentStorageException("Failed to store KYC document", ex);
        }
        return storageReference;
    }

    @Override
    public byte[] load(String storageReference) {
        Path source = resolveWithinBasePath(storageReference);
        try {
            return Files.readAllBytes(source);
        } catch (IOException ex) {
            throw new DocumentStorageException("Failed to load KYC document", ex);
        }
    }

    /**
     * Defends against a {@code storageReference} (however it originated)
     * resolving outside {@code basePath} — {@code storageReference} is
     * always our own generated value today, but this is the same
     * belt-and-suspenders normalization/containment check any filesystem
     * path built from a variable component should have.
     */
    private Path resolveWithinBasePath(String storageReference) {
        Path resolved = basePath.resolve(storageReference).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("storageReference resolves outside the storage root");
        }
        return resolved;
    }

    private String extensionOf(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        // Cap at 10 chars so a pathological "filename" can't be used to
        // smuggle an oversized/crafted suffix into the stored path.
        return (dotIndex >= 0 && originalFileName.length() - dotIndex <= 10)
                ? originalFileName.substring(dotIndex)
                : "";
    }
}
