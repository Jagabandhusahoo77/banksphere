import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { useAccounts } from "@/hooks/useAccounts";
import { useTransactions } from "@/hooks/useTransactions";
import Card from "@/components/common/Card";
import Select from "@/components/common/Select";
import Button from "@/components/common/Button";
import ErrorState from "@/components/common/ErrorState";
import TransactionTable from "@/components/banking/TransactionTable";
import { accountTypeLabel, maskAccountNumber } from "@/utils/format";

const PAGE_SIZE = 10;

export default function Transactions() {
  const { customerId } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const { accounts, loading: accountsLoading } = useAccounts(customerId);

  const [selectedAccountId, setSelectedAccountId] = useState<string>(searchParams.get("accountId") ?? "");
  const [page, setPage] = useState(0);

  useEffect(() => {
    if (!selectedAccountId && accounts.length > 0) {
      setSelectedAccountId(accounts[0].id);
    }
  }, [accounts, selectedAccountId]);

  const { transactions, totalPages, loading, error, reload } = useTransactions(
    selectedAccountId || null,
    page,
    PAGE_SIZE,
  );

  const handleAccountChange = (accountId: string) => {
    setSelectedAccountId(accountId);
    setPage(0);
    setSearchParams(accountId ? { accountId } : {});
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-h1 text-ink-primary">Transactions</h1>
          <p className="mt-1 text-body text-ink-secondary">Full transaction history for the selected account.</p>
        </div>
        {!accountsLoading && accounts.length > 0 && (
          <div className="w-full sm:w-72">
            <Select value={selectedAccountId} onChange={(e) => handleAccountChange(e.target.value)} aria-label="Select account">
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {accountTypeLabel(account.accountType)} · {maskAccountNumber(account.accountNumber)}
                </option>
              ))}
            </Select>
          </div>
        )}
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <Card>
          <TransactionTable transactions={transactions} loading={loading || accountsLoading} />

          {!loading && totalPages > 1 && (
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
      )}
    </div>
  );
}
