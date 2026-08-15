import { useCallback, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { accountService } from "@/services/accountService";
import { useAsync } from "@/hooks/useAsync";
import { useTransactions } from "@/hooks/useTransactions";
import { useToast } from "@/components/common/Toast";
import Card from "@/components/common/Card";
import Badge from "@/components/common/Badge";
import Button from "@/components/common/Button";
import Icon from "@/components/common/Icon";
import ErrorState from "@/components/common/ErrorState";
import { SkeletonCard } from "@/components/common/Skeleton";
import TransactionTable from "@/components/banking/TransactionTable";
import AmountInput from "@/components/forms/AmountInput";
import Input from "@/components/common/Input";
import { accountTypeLabel, formatMoney } from "@/utils/format";
import { getFriendlyErrorMessage } from "@/utils/apiError";

const STATUS_TONE = { ACTIVE: "success", INACTIVE: "neutral", CLOSED: "error" } as const;
const PAGE_SIZE = 10;

export default function AccountDetails() {
  const { id } = useParams<{ id: string }>();
  const { showToast } = useToast();

  const fetchAccount = useCallback(() => accountService.getAccount(id!), [id]);
  const { data: account, loading, error, reload } = useAsync(id ? fetchAccount : null, [id]);

  const [page, setPage] = useState(0);
  const { transactions, totalPages, loading: transactionsLoading, reload: reloadTransactions } = useTransactions(
    id ?? null,
    page,
    PAGE_SIZE,
  );

  const [activeTab, setActiveTab] = useState<"deposit" | "withdraw">("deposit");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return; // belt-and-braces against a double click racing the disabled-state re-render
    setFormError(null);

    const parsedAmount = Number(amount);
    if (!amount || Number.isNaN(parsedAmount) || parsedAmount <= 0) {
      setFormError("Enter an amount greater than zero.");
      return;
    }

    setSubmitting(true);
    try {
      const action = activeTab === "deposit" ? accountService.deposit : accountService.withdraw;
      await action(id!, { amount: parsedAmount, description: description || undefined });
      showToast(`${activeTab === "deposit" ? "Deposit" : "Withdrawal"} of ${formatMoney(parsedAmount, account?.currency ?? "USD")} successful.`, "success");
      setAmount("");
      setDescription("");
      reload();
      reloadTransactions();
    } catch (err) {
      // 422 (insufficient balance / account not active) already comes
      // through as the backend's own human-readable ErrorResponse.message
      // — getFriendlyErrorMessage shows it as-is by default, same as every
      // other status it doesn't have a specific override for.
      setFormError(getFriendlyErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (error) {
    return <ErrorState title="Couldn't load this account" message={error} onRetry={reload} />;
  }

  if (loading || !account) {
    return <SkeletonCard />;
  }

  const isActive = account.status === "ACTIVE";

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-body-sm text-ink-secondary">
        <Link to="/accounts" className="hover:text-brand-primary flex items-center gap-1">
          <Icon name="chevron-left" size={16} />
          Accounts
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <div className="flex items-start justify-between mb-5">
              <div>
                <p className="text-label text-brand-primary font-semibold">BankSphere</p>
                <p className="text-body-sm text-ink-secondary mt-0.5">{accountTypeLabel(account.accountType)}</p>
              </div>
              <Badge tone={STATUS_TONE[account.status]}>{account.status}</Badge>
            </div>

            <p className="text-display text-ink-primary">{formatMoney(account.balance, account.currency)}</p>
            <p className="mt-1 text-body-sm text-ink-muted">Available balance · {account.currency}</p>

            <dl className="mt-5 pt-4 border-t border-surface-border grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="min-w-0">
                <dt className="text-caption text-ink-muted">Account Number</dt>
                <dd className="flex items-center gap-2 mt-0.5">
                  <span className="font-mono text-body-sm text-ink-primary truncate">{account.accountNumber}</span>
                  <button
                    type="button"
                    aria-label="Copy account number"
                    onClick={async () => {
                      await navigator.clipboard.writeText(account.accountNumber);
                      showToast("Account number copied to clipboard.", "success");
                    }}
                    className="text-ink-muted hover:text-brand-primary shrink-0"
                  >
                    <Icon name="external-link" size={14} />
                  </button>
                </dd>
              </div>
              <div className="min-w-0">
                <dt className="text-caption text-ink-muted">IFSC</dt>
                <dd className="font-mono text-body-sm text-ink-primary mt-0.5">{account.ifsc}</dd>
              </div>
            </dl>

            <div className="mt-4 pt-4 border-t border-surface-border flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-caption text-ink-muted">Account ID</p>
                <p className="font-mono text-caption text-ink-secondary truncate">{account.id}</p>
              </div>
              <Button
                variant="outline"
                size="sm"
                icon="external-link"
                onClick={async () => {
                  await navigator.clipboard.writeText(account.id);
                  showToast("Account ID copied to clipboard.", "success");
                }}
              >
                Copy
              </Button>
            </div>
            <p className="mt-2 text-caption text-ink-muted">
              Share this with someone sending you a transfer from another BankSphere account.
            </p>
          </Card>

          <Card title="Transaction history">
            <TransactionTable transactions={transactions} loading={transactionsLoading} />
            {totalPages > 1 && (
              <div className="flex items-center justify-between mt-4 pt-4 border-t border-surface-border text-body-sm">
                <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))} icon="chevron-left">
                  Previous
                </Button>
                <span className="text-ink-muted">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  icon="chevron-right"
                  iconPosition="right"
                >
                  Next
                </Button>
              </div>
            )}
          </Card>
        </div>

        <div>
          <Card title="Deposit or withdraw">
            {!isActive ? (
              <p className="text-body-sm text-ink-secondary">
                This account is <strong>{account.status}</strong> and can't accept deposits or withdrawals.
              </p>
            ) : (
              <>
                <div className="flex gap-2 mb-5" role="tablist" aria-label="Transaction type">
                  <button
                    type="button"
                    role="tab"
                    aria-selected={activeTab === "deposit"}
                    onClick={() => {
                      setActiveTab("deposit");
                      setFormError(null);
                    }}
                    className={`flex-1 rounded-md py-2 text-body-sm font-medium transition-colors ${
                      activeTab === "deposit" ? "bg-brand-primary text-white" : "bg-surface-muted text-ink-secondary"
                    }`}
                  >
                    Deposit
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={activeTab === "withdraw"}
                    onClick={() => {
                      setActiveTab("withdraw");
                      setFormError(null);
                    }}
                    className={`flex-1 rounded-md py-2 text-body-sm font-medium transition-colors ${
                      activeTab === "withdraw" ? "bg-brand-primary text-white" : "bg-surface-muted text-ink-secondary"
                    }`}
                  >
                    Withdraw
                  </button>
                </div>

                <form onSubmit={handleSubmit} className="space-y-4">
                  <AmountInput
                    label="Amount"
                    value={amount}
                    onChange={setAmount}
                    currency={account.currency}
                    error={formError ?? undefined}
                  />
                  <Input
                    label="Description (optional)"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="e.g. ATM withdrawal"
                  />
                  <Button type="submit" variant="primary" fullWidth loading={submitting}>
                    {activeTab === "deposit" ? "Deposit funds" : "Withdraw funds"}
                  </Button>
                </form>
              </>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
