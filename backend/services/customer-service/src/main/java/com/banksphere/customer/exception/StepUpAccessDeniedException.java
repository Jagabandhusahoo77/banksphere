package com.banksphere.customer.exception;

/** A step-up challenge that exists but does not belong to the authenticated caller. Mapped to {@code 403}. */
public class StepUpAccessDeniedException extends RuntimeException {

    public StepUpAccessDeniedException() {
        super("This step-up challenge does not belong to the authenticated customer");
    }
}
