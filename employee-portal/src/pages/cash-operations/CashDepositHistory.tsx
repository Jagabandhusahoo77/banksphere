import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { operationsService } from "@/services/operationsService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import { formatDateTime, formatMoney, maskAccountNumber } from "@/utils/format";
import type { CashDepositHistoryEntry } from "@/types/operations";
import Card from "@/components/common/Card";
import Badge from "@/components/common/Badge";
import Button from "@/components/common/Button";
import Spinner from "@/components/common/Spinner";
import ErrorState from "@/components/common/ErrorState";
import EmptyState from "@/components/common/EmptyState";

export default function CashDepositHistory() {
  const [entries, setEntries] = useState<CashDepositHistoryEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    operationsService
      .cashDepositHistory()
      .then((response) => {
        if (!cancelled) setEntries(response);
      })
      .catch((err) => {
        if (!cancelled) setError(getFriendlyErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-h2 text-ink-primary">Cash Deposit History</h1>
          <p className="text-body-sm text-ink-secondary mt-1">Your branch's most recent cash deposits.</p>
        </div>
        <Link to="/cash-operations/deposit">
          <Button size="sm" icon="arrow-right" iconPosition="right">
            New deposit
          </Button>
        </Link>
      </div>

      {loading && (
        <div className="flex justify-center py-16">
          <Spinner label="Loading history" />
        </div>
      )}

      {!loading && error && <ErrorState message={error} />}

      {!loading && !error && entries && entries.length === 0 && (
        <EmptyState title="No cash deposits yet" message="Deposits your branch processes will appear here." />
      )}

      {!loading && !error && entries && entries.length > 0 && (
        <Card padding="none">
          <div className="table-scroll">
            <table className="w-full text-body-sm">
              <thead>
                <tr className="border-b border-surface-border text-left text-label text-ink-muted uppercase tracking-wide">
                  <th className="px-4 py-3">Operation</th>
                  <th className="px-4 py-3">Customer</th>
                  <th className="px-4 py-3">Account</th>
                  <th className="px-4 py-3">Amount</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">When</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.operationReference} className="border-b border-surface-border last:border-0">
                    <td className="px-4 py-3 text-ink-primary font-medium">{entry.operationReference}</td>
                    <td className="px-4 py-3 text-ink-secondary">{entry.customerName ?? "—"}</td>
                    <td className="px-4 py-3 text-ink-secondary">{maskAccountNumber(entry.accountNumber)}</td>
                    <td className="px-4 py-3 text-ink-primary">{formatMoney(entry.amount, entry.currency)}</td>
                    <td className="px-4 py-3">
                      <Badge tone={entry.status === "COMPLETED" ? "success" : "error"}>{entry.status}</Badge>
                    </td>
                    <td className="px-4 py-3 text-ink-secondary">{formatDateTime(entry.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
