package com.banksphere.customer.otp;

/**
 * Later architecture (not built this phase — see ADR-009):
 * <pre>
 * OtpService
 *     │
 *     ▼
 * OtpDeliveryProvider
 *     ├── Mock (this phase's only implementation)
 *     ├── SmsOtpDeliveryProvider
 *     ├── EmailOtpDeliveryProvider
 *     └── WhatsAppOtpDeliveryProvider
 * </pre>
 * The interface is deliberately minimal — {@code identifier}/{@code otp}/
 * {@code purpose} only — so swapping in a real provider later never
 * touches {@code OtpServiceImpl}'s business logic. No paid external
 * provider (Twilio, SES, WhatsApp Business API) is required for local
 * development.
 */
public interface OtpDeliveryProvider {

    void deliver(String identifier, String otp, OtpPurpose purpose);
}
