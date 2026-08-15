import { useState } from "react";
import { authService } from "@/services/authService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import type { DevOtpInboxEntry } from "@/types/auth";
import Button from "./Button";
import Icon from "./Icon";

/**
 * Phase 9D — local-development-only convenience for retrieving a
 * "delivered" OTP without a real SMS/email/WhatsApp provider (see
 * customer-service's MockOtpDeliveryProvider/DevOtpInboxController and
 * ADR-009). Gated on TWO independent layers, neither of which alone is
 * trusted: this component only renders when `import.meta.env.DEV` (a
 * production `vite build` strips it out entirely — dead code, not just
 * hidden UI), and even if it somehow rendered in production, the backend
 * route it calls does not exist in the application context at all unless
 * `banksphere.otp.dev-inbox.enabled=true`, so the request would just 404.
 *
 * `onSelectOtp` lets a caller (the OTP login screen, StepUpOtpModal) wire
 * "use this code" directly into its own OTP input, rather than making the
 * developer copy-paste by hand.
 */
export default function DevOtpInboxPanel({ onSelectOtp }: { onSelectOtp?: (otp: string) => void }) {
  const [entries, setEntries] = useState<DevOtpInboxEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);

  if (!import.meta.env.DEV) return null;

  const loadEntries = async () => {
    setLoading(true);
    setError(null);
    try {
      setEntries(await authService.getDevOtpInbox());
    } catch (err) {
      setError(getFriendlyErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const toggle = () => {
    const next = !expanded;
    setExpanded(next);
    if (next) void loadEntries();
  };

  return (
    <div className="mt-4 rounded-md border border-dashed border-semantic-warning/50 bg-semantic-warning/5">
      <button
        type="button"
        onClick={toggle}
        className="w-full flex items-center justify-between px-4 py-2.5 text-caption font-medium text-ink-secondary"
      >
        <span className="flex items-center gap-1.5">
          <Icon name="clock" size={14} />
          Dev OTP Inbox (local development only)
        </span>
        <Icon name={expanded ? "chevron-down" : "chevron-right"} size={14} />
      </button>

      {expanded && (
        <div className="px-4 pb-3 space-y-2">
          <div className="flex items-center justify-between">
            <p className="text-caption text-ink-muted">Most recently delivered codes, newest first.</p>
            <Button type="button" variant="ghost" size="sm" loading={loading} onClick={loadEntries}>
              Refresh
            </Button>
          </div>

          {error && <p className="text-caption text-semantic-error">{error}</p>}

          {!error && entries.length === 0 && !loading && (
            <p className="text-caption text-ink-muted">No OTPs delivered yet in this session.</p>
          )}

          <ul className="space-y-1.5">
            {entries.slice(0, 5).map((entry) => (
              <li
                key={entry.challengeId}
                className="flex items-center justify-between gap-2 rounded bg-white border border-surface-border px-3 py-2 text-caption"
              >
                <div className="min-w-0">
                  <p className="font-mono text-ink-primary">{entry.otp}</p>
                  <p className="text-ink-muted truncate">
                    {entry.purpose} · {entry.identifier}
                  </p>
                </div>
                {onSelectOtp && (
                  <Button type="button" variant="outline" size="sm" onClick={() => onSelectOtp(entry.otp)}>
                    Use
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
