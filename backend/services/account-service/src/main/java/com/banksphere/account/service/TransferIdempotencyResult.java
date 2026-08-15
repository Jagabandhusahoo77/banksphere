package com.banksphere.account.service;

import java.util.UUID;

/**
 * {@code alreadyCompleted() == true} means the caller should return
 * {@code responseSnapshot} verbatim without executing anything —
 * otherwise {@code recordId} identifies the {@code IN_PROGRESS} row this
 * attempt must later mark {@code COMPLETED}/{@code FAILED} (see {@link
 * TransferIdempotencyService}).
 */
public record TransferIdempotencyResult(boolean alreadyCompleted, String responseSnapshot, UUID recordId) {

    public static TransferIdempotencyResult completed(String responseSnapshot) {
        return new TransferIdempotencyResult(true, responseSnapshot, null);
    }

    public static TransferIdempotencyResult proceed(UUID recordId) {
        return new TransferIdempotencyResult(false, null, recordId);
    }
}
