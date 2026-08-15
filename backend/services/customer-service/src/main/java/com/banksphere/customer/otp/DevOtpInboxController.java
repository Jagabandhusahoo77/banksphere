package com.banksphere.customer.otp;

import com.banksphere.customer.otp.dto.DevOtpInboxEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Development-only convenience for retrieving a "delivered" OTP without a
 * real SMS/email/WhatsApp provider — see {@link DevOtpInbox}. This
 * controller <b>does not exist in the application context at all</b>
 * unless {@code banksphere.otp.dev-inbox.enabled=true} (the {@code
 * @ConditionalOnProperty} below, evaluated once at startup, not a
 * per-request check that could be bypassed) — so disabling it makes the
 * route genuinely absent (a plain 404 from Spring's own "no handler
 * found"), not merely hidden behind a permission check that a bug could
 * defeat. See ADR-009 and CLAUDE.md: this must never be reachable in a
 * real deployment. Deliberately PUBLIC (no auth) within SecurityConfig —
 * it exists specifically to be usable *before* login, and is meaningless
 * to gate behind the very credential it's helping the developer obtain.
 */
@RestController
@RequestMapping("/api/v1/auth/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "banksphere.otp.dev-inbox", name = "enabled", havingValue = "true")
public class DevOtpInboxController {

    private final DevOtpInbox devOtpInbox;

    @GetMapping("/otp-inbox")
    public ResponseEntity<List<DevOtpInboxEntryResponse>> recent() {
        List<DevOtpInboxEntryResponse> entries = devOtpInbox.recent().stream()
                .map(DevOtpInboxEntryResponse::from)
                .toList();
        return ResponseEntity.ok(entries);
    }
}
