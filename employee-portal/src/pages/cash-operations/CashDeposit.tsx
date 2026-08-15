import { useState, type FormEvent } from "react";
import { useAuth } from "@/context/AuthContext";
import { operationsService } from "@/services/operationsService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import { accountTypeLabel, formatMoney, maskAccountNumber } from "@/utils/format";
import type { AccountSummary, CashDepositResponse, CustomerSearchResponse } from "@/types/operations";
import Card from "@/components/common/Card";
import Button from "@/components/common/Button";
import Input from "@/components/common/Input";
import Badge from "@/components/common/Badge";

type Step = 1 | 2 | 3 | 4 | 5;

const STEP_LABELS: Record<Step, string> = {
  1: "Find Customer",
  2: "Select Account",
  3: "Amount",
  4: "Review",
  5: "Done",
};

export default function CashDeposit() {
  const { employee } = useAuth();
  const [step, setStep] = useState<Step>(1);

  const [searchValue, setSearchValue] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [searchResult, setSearchResult] = useState<CustomerSearchResponse | null>(null);

  const [selectedAccount, setSelectedAccount] = useState<AccountSummary | null>(null);

  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<CashDepositResponse | null>(null);

  if (!employee) return null;

  const handleSearch = async (event: FormEvent) => {
    event.preventDefault();
    if (searching || !searchValue.trim()) return;

    setSearching(true);
    setSearchError(null);
    setSearchResult(null);
    try {
      // A customer's own account number is the identifier a teller
      // realistically has in hand (they've been handed a passbook/card) —
      // see docs/architecture/employee-operations.md for why customer-id
      // search isn't exposed in this UI even though the backend supports it.
      const response = await operationsService.customerSearchByAccountNumber(searchValue.trim());
      setSearchResult(response);
      setStep(2);
    } catch (err) {
      setSearchError(getFriendlyErrorMessage(err, { 404: "No customer found with that account number." }));
    } finally {
      setSearching(false);
    }
  };

  const handleSelectAccount = (account: AccountSummary) => {
    setSelectedAccount(account);
    setStep(3);
  };

  const handleAmountNext = (event: FormEvent) => {
    event.preventDefault();
    const parsed = Number(amount);
    if (!amount || Number.isNaN(parsed) || parsed <= 0) return;
    setStep(4);
  };

  const handleConfirm = async () => {
    if (submitting || !selectedAccount) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await operationsService.cashDeposit({
        accountId: selectedAccount.id,
        amount: Number(amount),
        description: description.trim() || undefined,
      });
      setResult(response);
      setStep(5);
    } catch (err) {
      setSubmitError(getFriendlyErrorMessage(err, {
        403: "You're not authorized to credit this account — it may be outside your branch.",
        422: "This account can't receive a deposit right now (it may not be active).",
        409: "This account was just modified elsewhere — please try again.",
      }));
    } finally {
      setSubmitting(false);
    }
  };

  const startOver = () => {
    setStep(1);
    setSearchValue("");
    setSearchResult(null);
    setSelectedAccount(null);
    setAmount("");
    setDescription("");
    setSubmitError(null);
    setResult(null);
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-h2 text-ink-primary">Cash Deposit</h1>
        <p className="text-body-sm text-ink-secondary mt-1">Credit physical cash into a customer's BankSphere account.</p>
      </div>

      <ol className="flex flex-wrap gap-2 text-caption text-ink-muted">
        {([1, 2, 3, 4, 5] as Step[]).map((s) => (
          <li key={s} className={`px-2.5 py-1 rounded-pill ${s === step ? "bg-brand-primary-light text-brand-primary font-medium" : "bg-surface-muted"}`}>
            {s}. {STEP_LABELS[s]}
          </li>
        ))}
      </ol>

      {step === 1 && (
        <Card title="Find Customer">
          <form onSubmit={handleSearch} className="space-y-4" noValidate>
            <Input
              label="Recipient Account Number"
              hint="12-digit BankSphere account number"
              value={searchValue}
              onChange={(event) => setSearchValue(event.target.value.replace(/\D/g, "").slice(0, 12))}
              inputMode="numeric"
              maxLength={12}
            />
            {searchError && (
              <p role="alert" className="text-body-sm text-semantic-error bg-semantic-error-light rounded-md px-3 py-2">
                {searchError}
              </p>
            )}
            <Button type="submit" loading={searching} disabled={searchValue.length !== 12}>
              Search
            </Button>
          </form>
        </Card>
      )}

      {step === 2 && searchResult && (
        <Card title="Select Account" subtitle={`Customer: ${searchResult.customerName}`}>
          <div className="space-y-3">
            {searchResult.accounts.map((account) => (
              <button
                key={account.id}
                type="button"
                onClick={() => handleSelectAccount(account)}
                className="w-full text-left border border-surface-border rounded-md p-4 hover:border-brand-primary hover:bg-brand-primary-light transition-colors focus-visible:outline-none"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-body font-medium text-ink-primary">
                      {accountTypeLabel(account.accountType)} {maskAccountNumber(account.accountNumber)}
                    </p>
                    <p className="text-body-sm text-ink-secondary mt-0.5">
                      Balance: {formatMoney(account.balance, account.currency)}
                    </p>
                  </div>
                  <Badge tone={account.status === "ACTIVE" ? "success" : "neutral"}>{account.status}</Badge>
                </div>
              </button>
            ))}
            {searchResult.accounts.length === 0 && (
              <p className="text-body-sm text-ink-secondary">This customer has no accounts.</p>
            )}
          </div>
          <div className="mt-4">
            <Button variant="ghost" size="sm" onClick={startOver}>
              Back to search
            </Button>
          </div>
        </Card>
      )}

      {step === 3 && selectedAccount && (
        <Card title="Cash Deposit Amount">
          <form onSubmit={handleAmountNext} className="space-y-4" noValidate>
            <p className="text-body-sm text-ink-secondary">
              Depositing into {accountTypeLabel(selectedAccount.accountType)} {maskAccountNumber(selectedAccount.accountNumber)}
            </p>
            <Input
              label={`Amount (${selectedAccount.currency})`}
              value={amount}
              onChange={(event) => setAmount(event.target.value.replace(/[^0-9.]/g, ""))}
              inputMode="decimal"
              leadingText={selectedAccount.currency}
            />
            <Input
              label="Note (optional)"
              hint="Appended to the branch deposit description shown in the ledger"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={300}
            />
            <div className="flex gap-3">
              <Button type="button" variant="ghost" onClick={() => setStep(2)}>
                Back
              </Button>
              <Button type="submit" disabled={!amount || Number(amount) <= 0}>
                Continue
              </Button>
            </div>
          </form>
        </Card>
      )}

      {step === 4 && selectedAccount && searchResult && (
        <Card title="Review Cash Deposit">
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Customer</dt>
              <dd className="text-body text-ink-primary mt-1">{searchResult.customerName}</dd>
            </div>
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Account</dt>
              <dd className="text-body text-ink-primary mt-1">{maskAccountNumber(selectedAccount.accountNumber)}</dd>
            </div>
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Deposit Amount</dt>
              <dd className="text-h3 text-ink-primary mt-1">{formatMoney(Number(amount), selectedAccount.currency)}</dd>
            </div>
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Branch</dt>
              <dd className="text-body text-ink-primary mt-1">{employee.branch.branchName} ({employee.branch.branchCode})</dd>
            </div>
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Performed By</dt>
              <dd className="text-body text-ink-primary mt-1">{employee.employeeNumber}</dd>
            </div>
          </dl>

          {submitError && (
            <p role="alert" className="text-body-sm text-semantic-error bg-semantic-error-light rounded-md px-3 py-2 mb-4">
              {submitError}
            </p>
          )}

          <div className="flex gap-3">
            <Button variant="ghost" onClick={() => setStep(3)} disabled={submitting}>
              Back
            </Button>
            <Button onClick={handleConfirm} loading={submitting}>
              Confirm Cash Deposit
            </Button>
          </div>
        </Card>
      )}

      {step === 5 && result && (
        <Card>
          <div className="text-center py-6">
            <Badge tone="success">Cash deposit completed</Badge>
            <p className="text-h2 text-ink-primary mt-4">{formatMoney(result.newBalance, result.currency)}</p>
            <p className="text-body-sm text-ink-secondary mt-1">New balance on {maskAccountNumber(result.accountNumber)}</p>

            <dl className="text-left max-w-xs mx-auto mt-6 space-y-2">
              <div className="flex justify-between text-body-sm">
                <dt className="text-ink-muted">Operation</dt>
                <dd className="text-ink-primary font-medium">{result.operationReference}</dd>
              </div>
              <div className="flex justify-between text-body-sm">
                <dt className="text-ink-muted">Transaction</dt>
                <dd className="text-ink-primary font-medium">{result.transactionReference ?? "Recording pending"}</dd>
              </div>
              <div className="flex justify-between text-body-sm">
                <dt className="text-ink-muted">Branch</dt>
                <dd className="text-ink-primary font-medium">{result.branchCode}</dd>
              </div>
            </dl>

            <div className="mt-6">
              <Button onClick={startOver}>New cash deposit</Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
