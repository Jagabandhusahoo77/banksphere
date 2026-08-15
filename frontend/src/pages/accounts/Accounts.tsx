import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { useAccounts } from "@/hooks/useAccounts";
import { accountService } from "@/services/accountService";
import { useToast } from "@/components/common/Toast";
import AccountCard from "@/components/banking/AccountCard";
import Button from "@/components/common/Button";
import Modal from "@/components/common/Modal";
import Select from "@/components/common/Select";
import ErrorState from "@/components/common/ErrorState";
import EmptyState from "@/components/common/EmptyState";
import { SkeletonCard } from "@/components/common/Skeleton";
import AmountInput from "@/components/forms/AmountInput";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import type { Account, AccountType } from "@/types/account";

const CURRENCIES = ["INR", "USD", "EUR"] as const;

export default function Accounts() {
  const { customerId } = useAuth();
  const { accounts, loading, error, reload } = useAccounts(customerId);
  const { showToast } = useToast();

  const [modalOpen, setModalOpen] = useState(false);
  const [accountType, setAccountType] = useState<AccountType>("SAVINGS");
  const [currency, setCurrency] = useState<string>("INR");
  const [initialDeposit, setInitialDeposit] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [createdAccount, setCreatedAccount] = useState<Account | null>(null);

  const openModal = () => {
    setAccountType("SAVINGS");
    setCurrency("INR");
    setInitialDeposit("");
    setFormError(null);
    setCreatedAccount(null);
    setModalOpen(true);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    const parsedDeposit = initialDeposit ? Number(initialDeposit) : undefined;
    if (initialDeposit && (Number.isNaN(parsedDeposit) || (parsedDeposit ?? 0) < 0)) {
      setFormError("Initial deposit must be a valid, non-negative amount.");
      return;
    }

    setFormError(null);
    setSubmitting(true);
    try {
      // accountType/currency/initialDeposit only — accountNumber and ifsc
      // are never sent, because AccountCreateRequest has no such fields:
      // both are always generated/assigned server-side (see
      // AccountServiceImpl#createAccount).
      const account = await accountService.createAccount({ accountType, currency, initialDeposit: parsedDeposit });
      setCreatedAccount(account);
      showToast("Account opened successfully.", "success");
      reload();
    } catch (err) {
      setFormError(getFriendlyErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-h1 text-ink-primary">Accounts</h1>
          <p className="mt-1 text-body text-ink-secondary">All accounts held by this customer.</p>
        </div>
        <Button variant="primary" icon="plus" onClick={openModal}>
          Open new account
        </Button>
      </div>

      {error && <ErrorState message={error} onRetry={reload} />}

      {!error && loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}

      {!error && !loading && accounts.length === 0 && (
        <EmptyState
          title="No accounts found"
          description="Open your first account to start banking with BankSphere."
          action={
            <Button variant="primary" icon="plus" onClick={openModal}>
              Open new account
            </Button>
          }
        />
      )}

      {!error && !loading && accounts.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {accounts.map((account) => (
            <AccountCard key={account.id} account={account} />
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => !submitting && setModalOpen(false)} title={createdAccount ? "Account opened" : "Open a new account"}>
        {createdAccount ? (
          <div className="space-y-4">
            <p className="text-body-sm text-ink-secondary">
              Your new {createdAccount.accountType === "SAVINGS" ? "Savings" : "Current"} account is ready.
            </p>
            <dl className="rounded-md bg-surface-muted p-4 space-y-3">
              <div>
                <dt className="text-caption text-ink-muted">Account Number</dt>
                <dd className="font-mono text-body text-ink-primary mt-0.5">{createdAccount.accountNumber}</dd>
              </div>
              <div>
                <dt className="text-caption text-ink-muted">IFSC</dt>
                <dd className="font-mono text-body text-ink-primary mt-0.5">{createdAccount.ifsc}</dd>
              </div>
            </dl>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setModalOpen(false)}>
                Done
              </Button>
              <Link to={`/accounts/${createdAccount.id}`} onClick={() => setModalOpen(false)}>
                <Button variant="primary">View account</Button>
              </Link>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <Select label="Account type" value={accountType} onChange={(e) => setAccountType(e.target.value as AccountType)}>
              <option value="SAVINGS">Savings</option>
              <option value="CURRENT">Current</option>
            </Select>
            <Select label="Currency" value={currency} onChange={(e) => setCurrency(e.target.value)}>
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </Select>
            <AmountInput
              label="Initial deposit (optional)"
              value={initialDeposit}
              onChange={setInitialDeposit}
              currency={currency}
              hint="Leave blank to open with a zero balance."
            />

            {formError && (
              <p role="alert" className="text-body-sm text-semantic-error">
                {formError}
              </p>
            )}

            <p className="text-caption text-ink-muted">
              Your account number and IFSC are generated automatically by BankSphere — you don't need to enter them.
            </p>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={() => setModalOpen(false)} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" loading={submitting}>
                Open account
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
}
