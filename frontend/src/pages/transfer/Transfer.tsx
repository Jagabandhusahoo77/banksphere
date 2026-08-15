import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { useAccounts } from "@/hooks/useAccounts";
import { useBeneficiaries } from "@/hooks/useBeneficiaries";
import { accountService } from "@/services/accountService";
import { useToast } from "@/components/common/Toast";
import Card from "@/components/common/Card";
import Button from "@/components/common/Button";
import Badge from "@/components/common/Badge";
import Icon from "@/components/common/Icon";
import Input from "@/components/common/Input";
import ErrorState from "@/components/common/ErrorState";
import { SkeletonCard } from "@/components/common/Skeleton";
import StepUpOtpModal from "@/components/common/StepUpOtpModal";
import AmountInput from "@/components/forms/AmountInput";
import { accountTypeLabel, formatMoney, maskAccountNumber } from "@/utils/format";
import { ApiError, getFriendlyErrorMessage } from "@/utils/apiError";
import type { Account, ResolveRecipientResponse, TransferResponse } from "@/types/account";
import type { Beneficiary } from "@/types/beneficiary";

/**
 * Phase 9D — account-service's StepUpRequiredException always carries this
 * exact message (see its own javadoc); matching on it is how the frontend
 * tells "this transfer needs step-up" apart from every other 403 this
 * endpoint could return. There is no separate error code field in
 * ErrorResponse (message/status/error/path/details — see
 * account-service's GlobalExceptionHandler), so a message match is the
 * same approach getFriendlyErrorMessage already uses elsewhere for
 * status-based branching, just one level more specific.
 */
const STEP_UP_REQUIRED_MESSAGE = "Step-up authentication is required for this operation";

type RecipientMode = "own" | "beneficiary" | "manual";
type Step = 1 | 2 | 3 | 4 | 5;

const STEP_LABELS = ["Source", "Recipient", "Amount", "Review", "Done"];
const ACCOUNT_NUMBER_PATTERN = /^\d{12}$/;
const IFSC_PATTERN = /^[A-Z]{4}0[A-Z0-9]{6}$/;

/** Label for the recipient preview/review — honest about what we actually know (see ADR-005). */
function recipientDisplayName(mode: RecipientMode, ownAccount: Account | null, beneficiary: Beneficiary | null): string | null {
  if (mode === "own" && ownAccount) return accountTypeLabel(ownAccount.accountType);
  if (mode === "beneficiary" && beneficiary) return beneficiary.nickname || beneficiary.beneficiaryName;
  return null; // manual entry: no legitimate name source — see ADR-005, never fabricated.
}

export default function Transfer() {
  const { customerId } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const { accounts, loading: accountsLoading, error: accountsError, reload: reloadAccounts } = useAccounts(customerId);
  const { beneficiaries, loading: beneficiariesLoading } = useBeneficiaries(Boolean(customerId));

  const [step, setStep] = useState<Step>(1);
  const [sourceAccountId, setSourceAccountId] = useState<string>("");
  const [recipientMode, setRecipientMode] = useState<RecipientMode>("own");
  const [selectedOwnAccountId, setSelectedOwnAccountId] = useState("");
  const [selectedBeneficiaryId, setSelectedBeneficiaryId] = useState("");
  const [manualAccountNumber, setManualAccountNumber] = useState("");
  const [manualIfsc, setManualIfsc] = useState("");
  const [resolvedRecipient, setResolvedRecipient] = useState<ResolveRecipientResponse | null>(null);
  const [resolving, setResolving] = useState(false);
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [stepError, setStepError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<TransferResponse | null>(null);
  const [stepUpOpen, setStepUpOpen] = useState(false);

  // Phase 9D — stable across the initial attempt AND its step-up retry
  // (same logical operation), so a double-click/network-retry/browser-
  // refresh can never execute the same transfer twice. Regenerated each
  // time the customer reaches a fresh Review step — see goNext below.
  const idempotencyKeyRef = useRef<string>(crypto.randomUUID());

  const activeAccounts = useMemo(() => accounts.filter((a) => a.status === "ACTIVE"), [accounts]);
  const sourceAccount: Account | null = activeAccounts.find((a) => a.id === sourceAccountId) ?? null;
  const otherAccounts = useMemo(
    () => activeAccounts.filter((a) => a.id !== sourceAccountId),
    [activeAccounts, sourceAccountId],
  );
  const activeBeneficiaries = useMemo(() => beneficiaries.filter((b) => b.status === "ACTIVE"), [beneficiaries]);
  const selectedOwnAccount = otherAccounts.find((a) => a.id === selectedOwnAccountId) ?? null;
  const selectedBeneficiary = activeBeneficiaries.find((b) => b.id === selectedBeneficiaryId) ?? null;

  const parsedAmount = Number(amount);
  const amountValid = amount !== "" && !Number.isNaN(parsedAmount) && parsedAmount > 0;

  const resetRecipientSelection = () => {
    setSelectedOwnAccountId("");
    setSelectedBeneficiaryId("");
    setManualAccountNumber("");
    setManualIfsc("");
    setResolvedRecipient(null);
    setStepError(null);
  };

  // "One of my accounts" is already real, fetched, ownership-verified data
  // — no need to round-trip through resolve-recipient just to confirm what
  // the frontend already knows is true.
  useEffect(() => {
    if (recipientMode === "own" && selectedOwnAccount) {
      setResolvedRecipient({ accountNumber: selectedOwnAccount.accountNumber, ifsc: selectedOwnAccount.ifsc, bankName: "BankSphere" });
    }
  }, [recipientMode, selectedOwnAccount]);

  const handleResolveRecipient = async (accountNumber: string, ifsc: string) => {
    if (resolving) return;
    setStepError(null);
    setResolvedRecipient(null);
    setResolving(true);
    try {
      const response = await accountService.resolveRecipient({ accountNumber, ifsc });
      setResolvedRecipient(response);
    } catch (err) {
      setStepError(
        getFriendlyErrorMessage(err, {
          404: "We couldn't find a BankSphere account with that account number.",
          422: "That account can't receive transfers right now — check the IFSC, or the account may not be active.",
        }),
      );
    } finally {
      setResolving(false);
    }
  };

  const handleSelectBeneficiary = (beneficiary: Beneficiary) => {
    setSelectedBeneficiaryId(beneficiary.id);
    if (!description) {
      setDescription(`To ${beneficiary.beneficiaryName}`);
    }
    // Beneficiary details are only ever a saved label — always re-verify
    // the underlying account is still real and active before allowing a
    // transfer (it may have been closed since the beneficiary was saved).
    void handleResolveRecipient(beneficiary.accountNumber, beneficiary.ifsc);
  };

  const goNext = () => {
    setStepError(null);

    if (step === 1) {
      if (!sourceAccountId) {
        setStepError("Select an account to transfer from.");
        return;
      }
    }

    if (step === 2) {
      if (!resolvedRecipient) {
        setStepError("Verify the recipient before continuing.");
        return;
      }
      if (sourceAccount && resolvedRecipient.accountNumber === sourceAccount.accountNumber) {
        setStepError("Source and destination account must be different.");
        return;
      }
    }

    if (step === 3) {
      if (!amountValid) {
        setStepError("Enter an amount greater than zero.");
        return;
      }
      if (sourceAccount && parsedAmount > sourceAccount.balance) {
        // Client-side heads-up only — the backend remains the authority on
        // sufficient balance (a concurrent withdrawal could still make this
        // stale by submit time); this just avoids an obviously-doomed trip.
        setStepError(`Amount exceeds your available balance of ${formatMoney(sourceAccount.balance, sourceAccount.currency)}.`);
        return;
      }
    }

    setStep((current) => {
      const next = Math.min(5, current + 1) as Step;
      if (current === 3 && next === 4) {
        // A fresh review of (possibly new) operation details gets a fresh
        // idempotency key — reusing the same key across two DIFFERENT
        // operations would be a bug (see TransferIdempotencyRecord's
        // customer+key uniqueness), not a feature.
        idempotencyKeyRef.current = crypto.randomUUID();
      }
      return next;
    });
  };

  const goBack = () => {
    setStepError(null);
    setStep((current) => Math.max(1, current - 1) as Step);
  };

  const executeTransfer = async (stepUpChallengeId?: string) => {
    if (!resolvedRecipient) return;
    const response = await accountService.transfer({
      sourceAccountId,
      destinationAccountNumber: resolvedRecipient.accountNumber,
      destinationIfsc: resolvedRecipient.ifsc,
      amount: parsedAmount,
      description: description || undefined,
      stepUpChallengeId,
      idempotencyKey: idempotencyKeyRef.current,
    });
    setResult(response);
    showToast(`Transfer of ${formatMoney(response.amount, response.currency)} completed.`, "success");
    reloadAccounts();
    setStep(5);
  };

  const handleConfirm = async () => {
    if (submitting || !resolvedRecipient) return; // belt-and-braces against a double click racing the disabled-state re-render
    setSubmitting(true);
    setStepError(null);

    try {
      await executeTransfer();
    } catch (err) {
      // A 403 with this exact message means "backend policy requires
      // step-up for this amount" — not a rejection, a prompt for another
      // factor. The backend is the sole authority on this decision (see
      // StepUpPolicy/AccountServiceImpl.transfer) — the frontend never
      // decides on its own that step-up is or isn't needed.
      if (err instanceof ApiError && err.status === 403 && err.message === STEP_UP_REQUIRED_MESSAGE) {
        setStepUpOpen(true);
      } else {
        setStepError(
          getFriendlyErrorMessage(err, {
            422: "Insufficient available balance, or the accounts involved aren't both active with matching currencies.",
          }),
        );
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleStepUpVerified = async (challengeId: string) => {
    setStepUpOpen(false);
    setSubmitting(true);
    setStepError(null);
    try {
      await executeTransfer(challengeId);
    } catch (err) {
      setStepError(
        getFriendlyErrorMessage(err, {
          403: "That authorization is no longer valid — please try the transfer again.",
          422: "Insufficient available balance, or the accounts involved aren't both active with matching currencies.",
        }),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const startNewTransfer = () => {
    setStep(1);
    setSourceAccountId("");
    setRecipientMode("own");
    resetRecipientSelection();
    setAmount("");
    setDescription("");
    setStepError(null);
    setResult(null);
    idempotencyKeyRef.current = crypto.randomUUID();
  };

  if (accountsError) {
    return <ErrorState title="Couldn't load your accounts" message={accountsError} onRetry={reloadAccounts} />;
  }

  if (accountsLoading) {
    return <SkeletonCard />;
  }

  if (activeAccounts.length === 0) {
    return (
      <Card>
        <p className="text-h3 text-ink-primary">No active accounts</p>
        <p className="mt-1.5 text-body-sm text-ink-secondary">
          You need at least one active account to make a transfer.
        </p>
      </Card>
    );
  }

  const recipientName = recipientDisplayName(recipientMode, selectedOwnAccount, selectedBeneficiary);

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-h1 text-ink-primary">Transfer Money</h1>
        <p className="mt-1 text-body text-ink-secondary">Move money between accounts, atomically and securely.</p>
      </div>

      {step < 5 && (
        <ol className="flex items-center gap-2" aria-label="Transfer steps">
          {STEP_LABELS.slice(0, 4).map((label, index) => {
            const stepNumber = index + 1;
            const isCurrent = stepNumber === step;
            const isDone = stepNumber < step;
            return (
              <li key={label} className="flex items-center gap-2 flex-1">
                <span
                  className={`flex items-center justify-center w-7 h-7 rounded-full text-caption font-semibold shrink-0 ${
                    isCurrent
                      ? "bg-brand-primary text-white"
                      : isDone
                        ? "bg-brand-primary-light text-brand-primary"
                        : "bg-surface-muted text-ink-muted"
                  }`}
                >
                  {isDone ? <Icon name="check" size={14} /> : stepNumber}
                </span>
                <span className={`text-caption ${isCurrent ? "text-ink-primary font-medium" : "text-ink-muted"} hidden sm:inline`}>
                  {label}
                </span>
                {stepNumber < 4 && <span className="flex-1 h-px bg-surface-border" aria-hidden="true" />}
              </li>
            );
          })}
        </ol>
      )}

      <Card>
        {step === 1 && (
          <div className="space-y-4">
            <h2 className="text-h3 text-ink-primary">From which account?</h2>
            <div className="space-y-2">
              {activeAccounts.map((account) => (
                <button
                  key={account.id}
                  type="button"
                  onClick={() => setSourceAccountId(account.id)}
                  className={`w-full text-left rounded-md border px-4 py-3 transition-colors ${
                    sourceAccountId === account.id
                      ? "border-brand-primary bg-brand-primary-light"
                      : "border-surface-border hover:bg-surface-muted"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-body-sm font-medium text-ink-primary">{accountTypeLabel(account.accountType)}</p>
                      <p className="font-mono text-caption text-ink-muted mt-0.5">{maskAccountNumber(account.accountNumber)}</p>
                    </div>
                    <p className="text-body font-medium text-ink-primary">{formatMoney(account.balance, account.currency)}</p>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
            <h2 className="text-h3 text-ink-primary">Who are you sending to?</h2>

            <div className="flex gap-2" role="tablist" aria-label="Recipient type">
              {(
                [
                  ["own", "My accounts"],
                  ["beneficiary", "Saved beneficiary"],
                  ["manual", "Account number"],
                ] as const
              ).map(([mode, label]) => (
                <button
                  key={mode}
                  type="button"
                  role="tab"
                  aria-selected={recipientMode === mode}
                  onClick={() => {
                    setRecipientMode(mode);
                    resetRecipientSelection();
                  }}
                  className={`flex-1 rounded-md py-2 text-caption sm:text-body-sm font-medium transition-colors ${
                    recipientMode === mode ? "bg-brand-primary text-white" : "bg-surface-muted text-ink-secondary"
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>

            {recipientMode === "own" &&
              (otherAccounts.length === 0 ? (
                <p className="text-body-sm text-ink-secondary">
                  You don't have another active account to transfer to. Open a second account, or switch to
                  "Saved beneficiary" or "Account number."
                </p>
              ) : (
                <div className="space-y-2">
                  {otherAccounts.map((account) => (
                    <button
                      key={account.id}
                      type="button"
                      onClick={() => setSelectedOwnAccountId(account.id)}
                      className={`w-full text-left rounded-md border px-4 py-3 transition-colors ${
                        selectedOwnAccountId === account.id
                          ? "border-brand-primary bg-brand-primary-light"
                          : "border-surface-border hover:bg-surface-muted"
                      }`}
                    >
                      <p className="text-body-sm font-medium text-ink-primary">{accountTypeLabel(account.accountType)}</p>
                      <p className="font-mono text-caption text-ink-muted mt-0.5">{maskAccountNumber(account.accountNumber)}</p>
                    </button>
                  ))}
                </div>
              ))}

            {recipientMode === "beneficiary" &&
              (beneficiariesLoading ? (
                <p className="text-body-sm text-ink-muted">Loading…</p>
              ) : activeBeneficiaries.length === 0 ? (
                <p className="text-body-sm text-ink-secondary">
                  No saved beneficiaries yet. <Link to="/beneficiaries" className="text-brand-primary hover:underline">Add one</Link>.
                </p>
              ) : (
                <div className="space-y-2">
                  {activeBeneficiaries.map((beneficiary) => (
                    <button
                      key={beneficiary.id}
                      type="button"
                      onClick={() => handleSelectBeneficiary(beneficiary)}
                      className={`w-full text-left rounded-md border px-4 py-3 transition-colors ${
                        selectedBeneficiaryId === beneficiary.id
                          ? "border-brand-primary bg-brand-primary-light"
                          : "border-surface-border hover:bg-surface-muted"
                      }`}
                    >
                      <p className="text-body-sm font-medium text-ink-primary">
                        {beneficiary.nickname || beneficiary.beneficiaryName}
                      </p>
                      <p className="text-caption text-ink-muted mt-0.5">
                        {beneficiary.bankName} · {maskAccountNumber(beneficiary.accountNumber)}
                      </p>
                    </button>
                  ))}
                </div>
              ))}

            {recipientMode === "manual" && (
              <div className="space-y-4">
                <Input
                  label="Recipient Account Number"
                  value={manualAccountNumber}
                  onChange={(e) => {
                    setManualAccountNumber(e.target.value.replace(/\D/g, "").slice(0, 12));
                    setResolvedRecipient(null);
                  }}
                  placeholder="e.g. 617242043877"
                  inputMode="numeric"
                  hint="The recipient's 12-digit BankSphere account number."
                />
                <Input
                  label="IFSC Code"
                  value={manualIfsc}
                  onChange={(e) => {
                    setManualIfsc(e.target.value.toUpperCase().slice(0, 11));
                    setResolvedRecipient(null);
                  }}
                  placeholder="e.g. BANK0000001"
                />
                <Button
                  type="button"
                  variant="outline"
                  loading={resolving}
                  disabled={!ACCOUNT_NUMBER_PATTERN.test(manualAccountNumber) || !IFSC_PATTERN.test(manualIfsc)}
                  onClick={() => handleResolveRecipient(manualAccountNumber, manualIfsc)}
                >
                  Verify recipient
                </Button>
              </div>
            )}

            {resolvedRecipient && (recipientMode === "beneficiary" || recipientMode === "manual") && (
              <div className="rounded-md bg-semantic-success-light p-4">
                <p className="text-label text-ink-secondary">Recipient</p>
                {recipientName && <p className="text-body font-medium text-ink-primary mt-0.5">{recipientName}</p>}
                <p className="text-body-sm text-ink-primary mt-0.5">{resolvedRecipient.bankName}</p>
                <p className="text-caption text-ink-muted mt-0.5">
                  Account ending {resolvedRecipient.accountNumber.slice(-4)} · IFSC {resolvedRecipient.ifsc}
                </p>
              </div>
            )}
          </div>
        )}

        {step === 3 && sourceAccount && (
          <div className="space-y-4">
            <h2 className="text-h3 text-ink-primary">How much?</h2>
            <AmountInput label="Amount" value={amount} onChange={setAmount} currency={sourceAccount.currency} />
            <div className="rounded-md bg-surface-muted p-4 space-y-1.5 text-body-sm">
              <div className="flex justify-between">
                <span className="text-ink-secondary">Available balance</span>
                <span className="text-ink-primary font-medium">{formatMoney(sourceAccount.balance, sourceAccount.currency)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-ink-secondary">Transfer amount</span>
                <span className="text-ink-primary font-medium">
                  {amountValid ? formatMoney(parsedAmount, sourceAccount.currency) : "—"}
                </span>
              </div>
              <div className="flex justify-between pt-1.5 border-t border-surface-border">
                <span className="text-ink-secondary">Remaining balance</span>
                <span className="text-ink-primary font-semibold">
                  {amountValid ? formatMoney(sourceAccount.balance - parsedAmount, sourceAccount.currency) : "—"}
                </span>
              </div>
              <p className="text-caption text-ink-muted pt-1">
                This is an estimate — the backend recalculates and authorizes the actual balance.
              </p>
            </div>
            <Input
              label="Description (optional)"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Rent for August"
            />
          </div>
        )}

        {step === 4 && sourceAccount && resolvedRecipient && (
          <div className="space-y-5">
            <h2 className="text-h3 text-ink-primary">Review transfer</h2>

            <div className="space-y-4">
              <div>
                <p className="text-label text-ink-secondary">From</p>
                <p className="text-body text-ink-primary mt-0.5">
                  {accountTypeLabel(sourceAccount.accountType)} {maskAccountNumber(sourceAccount.accountNumber)}
                </p>
              </div>

              <div>
                <p className="text-label text-ink-secondary">To</p>
                {recipientName && <p className="text-body text-ink-primary mt-0.5">{recipientName}</p>}
                <p className="text-body-sm text-ink-secondary mt-0.5">{resolvedRecipient.bankName}</p>
                <p className="font-mono text-caption text-ink-muted mt-0.5">
                  {maskAccountNumber(resolvedRecipient.accountNumber)}
                </p>
              </div>

              <div>
                <p className="text-label text-ink-secondary">IFSC</p>
                <p className="text-body text-ink-primary mt-0.5">{resolvedRecipient.ifsc}</p>
              </div>

              <div>
                <p className="text-label text-ink-secondary">Amount</p>
                <p className="text-h2 text-ink-primary mt-0.5">{formatMoney(parsedAmount, sourceAccount.currency)}</p>
              </div>

              {description && (
                <div>
                  <p className="text-label text-ink-secondary">Description</p>
                  <p className="text-body-sm text-ink-secondary mt-0.5">{description}</p>
                </div>
              )}
            </div>
          </div>
        )}

        {step === 5 && (
          <div className="text-center py-4">
            {result ? (
              <>
                <span className="flex items-center justify-center w-14 h-14 rounded-full bg-semantic-success-light text-semantic-success mx-auto mb-4">
                  <Icon name="check-circle" size={28} />
                </span>
                <p className="text-h2 text-ink-primary">Transfer successful</p>
                <p className="mt-2 text-h1 text-ink-primary">{formatMoney(result.amount, result.currency)}</p>
                <p className="mt-1 text-body-sm text-ink-secondary">
                  To {result.destinationIfsc} account ending {result.destinationAccountNumber.slice(-4)}
                </p>
                <div className="mt-4 inline-flex flex-col items-start gap-1 text-left">
                  <Badge tone="success">{result.status}</Badge>
                  <p className="text-caption text-ink-muted mt-1">
                    Transfer ID: <span className="font-mono">{result.transferId}</span>
                  </p>
                  <p className="text-caption text-ink-muted">
                    This is BankSphere's transfer correlation ID, not a transaction-service ledger reference — the
                    debit and credit each get their own reference in Transaction History.
                  </p>
                </div>
                <div className="mt-6 flex flex-col sm:flex-row gap-3 justify-center">
                  <Button variant="outline" onClick={() => navigate(`/transactions?accountId=${result.sourceAccountId}`)}>
                    View Transactions
                  </Button>
                  <Button variant="primary" onClick={() => navigate("/dashboard")}>
                    Back to Dashboard
                  </Button>
                </div>
                <button type="button" onClick={startNewTransfer} className="mt-4 text-body-sm text-brand-primary hover:underline">
                  Make another transfer
                </button>
              </>
            ) : null}
          </div>
        )}

        {stepError && step < 5 && (
          <p role="alert" className="mt-4 text-body-sm text-semantic-error">
            {stepError}
          </p>
        )}

        {step < 5 && (
          <div className="flex items-center justify-between mt-6 pt-4 border-t border-surface-border">
            <Button variant="ghost" onClick={goBack} disabled={step === 1 || submitting}>
              Back
            </Button>
            {step < 4 ? (
              <Button variant="primary" onClick={goNext}>
                Continue
              </Button>
            ) : (
              <Button variant="primary" onClick={handleConfirm} loading={submitting}>
                Confirm &amp; Transfer
              </Button>
            )}
          </div>
        )}
      </Card>

      {sourceAccount && resolvedRecipient && (
        <StepUpOtpModal
          open={stepUpOpen}
          onClose={() => setStepUpOpen(false)}
          onVerified={handleStepUpVerified}
          transferContext={{
            sourceAccountId,
            destinationAccountNumber: resolvedRecipient.accountNumber,
            destinationIfsc: resolvedRecipient.ifsc,
            amount: parsedAmount,
            currency: sourceAccount.currency,
          }}
          operationSummary={
            <>
              Send <span className="font-semibold">{formatMoney(parsedAmount, sourceAccount.currency)}</span> to
              account ending {resolvedRecipient.accountNumber.slice(-4)}
              {recipientName ? ` (${recipientName})` : ""}.
            </>
          }
        />
      )}
    </div>
  );
}
