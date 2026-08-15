import { useEffect, useState, type ReactNode } from "react";
import { stepUpService } from "@/services/stepUpService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import type { StepUpTransferContext } from "@/types/auth";
import Modal from "./Modal";
import Input from "./Input";
import Button from "./Button";
import Icon from "./Icon";
import DevOtpInboxPanel from "./DevOtpInboxPanel";

interface StepUpOtpModalProps {
  open: boolean;
  onClose: () => void;
  /** Called once the OTP is verified — passes the challengeId the caller must resubmit its operation with (see Transfer.tsx). Never called for a rejected/expired OTP. */
  onVerified: (challengeId: string) => void;
  /** The exact operation this step-up authorizes — must match what actually executes, or account-service's own recomputed-hash check rejects it (see ADR-009). */
  transferContext: StepUpTransferContext;
  /** e.g. "Send ₹1,00,000 to account ending 4821" — shown so the customer can confirm what they're authorizing before entering a code. */
  operationSummary: ReactNode;
}

/**
 * Phase 9D — step-up authentication for a high-risk operation. This is
 * NOT login: the customer is already authenticated; this proves intent
 * for one specific, already-defined operation. A fresh challenge is
 * requested every time this modal opens (never reused across two
 * different operations, and never reused after a successful verify — see
 * OtpServiceImpl#requestStepUp/confirmStepUpExecution). The caller
 * (Transfer.tsx) is responsible for actually executing the operation with
 * the returned challengeId — this modal only proves "it's really you,"
 * it never calls a banking endpoint itself (see docs/chatbot/security.md's
 * same intent-detection-vs-execution boundary, applied here to step-up).
 */
export default function StepUpOtpModal({ open, onClose, onVerified, transferContext, operationSummary }: StepUpOtpModalProps) {
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [otp, setOtp] = useState("");
  const [requesting, setRequesting] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setChallengeId(null);
    setOtp("");
    setError(null);
    setRequesting(true);
    stepUpService
      .requestTransferStepUp(transferContext)
      .then((response) => setChallengeId(response.challengeId))
      .catch((err) => setError(getFriendlyErrorMessage(err)))
      .finally(() => setRequesting(false));
    // Only re-request when the modal transitions open — not on every
    // transferContext object-identity change while it's already open.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const handleVerify = async () => {
    if (!challengeId || otp.trim().length < 4 || verifying) return;
    setVerifying(true);
    setError(null);
    try {
      await stepUpService.verifyStepUp(challengeId, otp.trim());
      onVerified(challengeId);
    } catch (err) {
      setError(getFriendlyErrorMessage(err, { 400: "That code is incorrect or has expired. Please try again." }));
    } finally {
      setVerifying(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Verify it's you">
      <div className="space-y-4">
        <div className="flex items-start gap-2.5 rounded-md bg-surface-muted px-3.5 py-3">
          <Icon name="shield-check" size={18} className="text-brand-primary shrink-0 mt-0.5" />
          <div className="text-body-sm text-ink-primary">{operationSummary}</div>
        </div>

        <p className="text-caption text-ink-muted">
          This is a higher-value operation, so we need to confirm it's really you. Enter the 6-digit code we sent
          you.
        </p>

        {requesting && <p className="text-body-sm text-ink-secondary">Sending code…</p>}

        {!requesting && challengeId && (
          <>
            <Input
              label="6-digit code"
              inputMode="numeric"
              autoComplete="one-time-code"
              autoFocus
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 8))}
              placeholder="123456"
              error={error ?? undefined}
            />
            <div className="flex gap-3">
              <Button type="button" variant="ghost" onClick={onClose} disabled={verifying}>
                Cancel
              </Button>
              <Button type="button" variant="primary" fullWidth loading={verifying} onClick={handleVerify}>
                {verifying ? "Verifying…" : "Verify"}
              </Button>
            </div>
            <DevOtpInboxPanel onSelectOtp={setOtp} />
          </>
        )}

        {!requesting && !challengeId && error && (
          <>
            <p className="text-body-sm text-semantic-error" role="alert">
              {error}
            </p>
            <Button type="button" variant="ghost" onClick={onClose}>
              Close
            </Button>
          </>
        )}
      </div>
    </Modal>
  );
}
