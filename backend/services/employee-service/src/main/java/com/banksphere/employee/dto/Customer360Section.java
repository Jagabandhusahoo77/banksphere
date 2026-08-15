package com.banksphere.employee.dto;

/**
 * Wraps one section of {@link Customer360Response} with its own
 * permission-based availability — the section-level graceful-degradation
 * pattern Phase 9C introduces (see ADR-008): the aggregation as a whole
 * never 403s just because the caller lacks one section's permission,
 * only the sections the caller isn't authorized for come back {@code
 * available: false} with a reason, while every section the caller IS
 * authorized for is still populated with real data.
 *
 * <p>{@code available: true} with a null/empty {@code data} is a
 * DIFFERENT, unrelated state — it means the caller CAN see this section,
 * there is simply nothing there yet (e.g. the customer has never started
 * KYC, or has zero active beneficiaries).
 */
public record Customer360Section<T>(boolean available, String unavailableReason, T data) {

    public static <T> Customer360Section<T> of(T data) {
        return new Customer360Section<>(true, null, data);
    }

    public static <T> Customer360Section<T> unavailable(String reason) {
        return new Customer360Section<>(false, reason, null);
    }
}
