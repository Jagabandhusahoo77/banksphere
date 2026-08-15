import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { useCustomer } from "@/hooks/useCustomer";
import { useAccounts } from "@/hooks/useAccounts";
import { useTransactions } from "@/hooks/useTransactions";
import BalanceCard from "@/components/banking/BalanceCard";
import QuickAction from "@/components/banking/QuickAction";
import TransactionTable from "@/components/banking/TransactionTable";
import Card from "@/components/common/Card";
import ErrorState from "@/components/common/ErrorState";
import { SkeletonCard, SkeletonText } from "@/components/common/Skeleton";
import EmptyState from "@/components/common/EmptyState";
import ChartCard from "@/components/charts/ChartCard";
import LegendSwatch from "@/components/charts/LegendSwatch";
import LineChart from "@/components/charts/LineChart";
import BarChart from "@/components/charts/BarChart";
import PieChart from "@/components/charts/PieChart";
import { CHART_COLORS } from "@/components/charts/chartColors";
import { timeOfDayGreeting, formatMoney, accountTypeLabel, maskAccountNumber } from "@/utils/format";
import { transactionSignedAmount } from "@/utils/transactionDirection";

const CHART_TRANSACTION_LIMIT = 30;
const dateLabel = (iso: string) => new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric" }).format(new Date(iso));

export default function Dashboard() {
  const { customerId } = useAuth();
  const { customer, loading: customerLoading, error: customerError, reload: reloadCustomer } = useCustomer(customerId);
  const { accounts, loading: accountsLoading, error: accountsError, reload: reloadAccounts } = useAccounts(customerId);

  const primaryAccount = accounts[0] ?? null;
  const { transactions, loading: transactionsLoading } = useTransactions(primaryAccount?.id ?? null, 0, CHART_TRANSACTION_LIMIT);
  const recentTransactions = transactions.slice(0, 5);

  const savingsAccount = accounts.find((a) => a.accountType === "SAVINGS") ?? null;
  const currentAccount = accounts.find((a) => a.accountType === "CURRENT") ?? null;

  const totalsByCurrency = useMemo(() => {
    const totals = new Map<string, number>();
    for (const account of accounts) {
      totals.set(account.currency, (totals.get(account.currency) ?? 0) + account.balance);
    }
    return Array.from(totals.entries());
  }, [accounts]);

  // Reconstructs a historical running balance from real transaction deltas —
  // the backend only exposes the *current* balance, not a balance-over-time
  // series, so this walks the (newest-first) transaction list backward from
  // today's balance, undoing each deposit/withdrawal to recover what the
  // balance was before it. Every point is a real, derived value; nothing is
  // interpolated or fabricated. One known imprecision: transactionSignedAmount
  // returns 0 for a TRANSFER whose direction can't be inferred (the sender
  // supplied a custom description on both ledger legs — see
  // utils/transactionDirection.ts), so a reconstructed point before such a
  // transfer can be off by that amount. This is a downstream consequence of
  // a real backend/ledger-model gap, not a frontend rounding bug.
  const balanceTrend = useMemo(() => {
    if (!primaryAccount) return [];
    let runningBalance = primaryAccount.balance;
    const points: { label: string; value: number }[] = [];
    for (const txn of transactions) {
      points.push({ label: dateLabel(txn.createdAt), value: runningBalance });
      runningBalance -= transactionSignedAmount(txn);
    }
    points.push({ label: "Start", value: runningBalance });
    return points.reverse();
  }, [primaryAccount, transactions]);

  // Deposits vs withdrawals, grouped by calendar day — every value is a real
  // sum of fetched transaction amounts, capped to the most recent 8 active
  // days so the chart stays readable regardless of how much history exists.
  const depositsVsWithdrawals = useMemo(() => {
    const byDay = new Map<string, { date: Date; deposits: number; withdrawals: number }>();
    for (const txn of transactions) {
      const date = new Date(txn.createdAt);
      const key = date.toISOString().slice(0, 10);
      const entry = byDay.get(key) ?? { date, deposits: 0, withdrawals: 0 };
      if (txn.transactionType === "DEPOSIT") entry.deposits += txn.amount;
      else if (txn.transactionType === "WITHDRAWAL") entry.withdrawals += txn.amount;
      byDay.set(key, entry);
    }
    return Array.from(byDay.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .slice(-8)
      .map(([, entry]) => ({ label: dateLabel(entry.date.toISOString()), deposits: entry.deposits, withdrawals: entry.withdrawals }));
  }, [transactions]);

  // Balance split across this customer's own accounts, scoped to a single
  // currency (never summed across currencies — see CLAUDE.md's money-display
  // rule). Only rendered when there are 3+ accounts in that currency: a
  // 1-slice pie is just the total, and a 2-slice pie is a documented
  // anti-pattern (a bar, or the two numbers, reads better) — the existing
  // balance cards above already cover 1–2 accounts perfectly well.
  const accountBalanceSlices = useMemo(() => {
    const byCurrency = new Map<string, typeof accounts>();
    for (const account of accounts) {
      byCurrency.set(account.currency, [...(byCurrency.get(account.currency) ?? []), account]);
    }
    let best: typeof accounts = [];
    for (const group of byCurrency.values()) {
      if (group.length > best.length) best = group;
    }
    if (best.length < 3) return null;
    return {
      currency: best[0].currency,
      slices: best.map((a) => ({ label: `${accountTypeLabel(a.accountType)} ${maskAccountNumber(a.accountNumber)}`, value: a.balance })),
    };
  }, [accounts]);

  const loading = customerLoading || accountsLoading;
  const error = customerError || accountsError;

  if (error) {
    return (
      <ErrorState
        message={error}
        onRetry={() => {
          reloadCustomer();
          reloadAccounts();
        }}
      />
    );
  }

  return (
    <div className="space-y-8">
      <div>
        {loading ? (
          <SkeletonText lines={2} className="max-w-xs" />
        ) : (
          <>
            <h1 className="text-h1 text-ink-primary">
              {timeOfDayGreeting()}
              {customer ? `, ${customer.firstName}` : ""}
            </h1>
            <p className="mt-1 text-body text-ink-secondary">Here's your financial overview.</p>
          </>
        )}
      </div>

      {/* Balance cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {loading ? (
          <>
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
          </>
        ) : accounts.length === 0 ? (
          <div className="lg:col-span-3">
            <Card>
              <EmptyState
                title="No accounts yet"
                description="Once an account is opened for this customer, its balance will appear here."
              />
            </Card>
          </div>
        ) : (
          <>
            {totalsByCurrency.map(([currency, total]) => (
              <BalanceCard
                key={currency}
                label={totalsByCurrency.length > 1 ? `Total Balance (${currency})` : "Total Balance"}
                amount={total}
                currency={currency}
                tone="primary"
                icon="wallet"
                helperText={`Across ${accounts.length} account${accounts.length === 1 ? "" : "s"}`}
              />
            ))}
            {savingsAccount && (
              <BalanceCard label="Savings Account" amount={savingsAccount.balance} currency={savingsAccount.currency} icon="wallet" />
            )}
            {currentAccount && (
              <BalanceCard
                label="Current Account"
                amount={currentAccount.balance}
                currency={currentAccount.currency}
                icon="credit-card"
              />
            )}
          </>
        )}
      </div>

      {/* Quick actions */}
      <div>
        <h2 className="text-h3 text-ink-primary mb-4">Quick actions</h2>
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          <QuickAction icon="arrow-right" label="Transfer Money" to="/transfer" />
          <QuickAction icon="plus" label="Deposit" to={primaryAccount ? `/accounts/${primaryAccount.id}` : undefined} />
          <QuickAction icon="minus" label="Withdraw" to={primaryAccount ? `/accounts/${primaryAccount.id}` : undefined} />
          <QuickAction icon="credit-card" label="Pay Bills" comingSoon />
          <QuickAction icon="list" label="View Transactions" to="/transactions" />
        </div>
      </div>

      {/* Financial overview charts — every chart is built from this customer's
          own real account/transaction data (see the transform comments
          above), never illustrative/placeholder figures. */}
      {!loading && primaryAccount && (
        <div>
          <h2 className="text-h3 text-ink-primary mb-4">Financial overview</h2>
          <div className={`grid grid-cols-1 ${accountBalanceSlices ? "lg:grid-cols-3" : ""} gap-4`}>
            <div className={accountBalanceSlices ? "lg:col-span-2" : ""}>
              <ChartCard
                title="Balance trend"
                subtitle={`${accountTypeLabel(primaryAccount.accountType)} ${maskAccountNumber(primaryAccount.accountNumber)}`}
                chart={<LineChart data={balanceTrend} currency={primaryAccount.currency} />}
                table={
                  <div className="table-scroll">
                    <table className="w-full text-body-sm">
                      <thead>
                        <tr className="text-left text-caption text-ink-muted border-b border-surface-border">
                          <th className="py-2 pr-4 font-medium">Date</th>
                          <th className="py-2 font-medium">Balance</th>
                        </tr>
                      </thead>
                      <tbody>
                        {balanceTrend.map((point) => (
                          <tr key={point.label} className="border-b border-surface-border last:border-0">
                            <td className="py-2 pr-4 text-ink-secondary">{point.label}</td>
                            <td className="py-2 font-medium text-ink-primary">{formatMoney(point.value, primaryAccount.currency)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                }
              />
            </div>

            {accountBalanceSlices && (
              <ChartCard
                title="Balance by account"
                chart={<PieChart data={accountBalanceSlices.slices} currency={accountBalanceSlices.currency} />}
                table={
                  <table className="w-full text-body-sm">
                    <thead>
                      <tr className="text-left text-caption text-ink-muted border-b border-surface-border">
                        <th className="py-2 pr-4 font-medium">Account</th>
                        <th className="py-2 font-medium">Balance</th>
                      </tr>
                    </thead>
                    <tbody>
                      {accountBalanceSlices.slices.map((slice) => (
                        <tr key={slice.label} className="border-b border-surface-border last:border-0">
                          <td className="py-2 pr-4 text-ink-secondary">{slice.label}</td>
                          <td className="py-2 font-medium text-ink-primary">{formatMoney(slice.value, accountBalanceSlices.currency)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                }
              />
            )}
          </div>

          <div className="mt-4">
            <ChartCard
              title="Deposits vs withdrawals"
              subtitle="By day, most recent activity"
              legend={
                <>
                  <LegendSwatch color={CHART_COLORS.seriesBlue} label="Deposits" />
                  <LegendSwatch color={CHART_COLORS.seriesGold} label="Withdrawals" />
                </>
              }
              chart={<BarChart data={depositsVsWithdrawals} currency={primaryAccount.currency} />}
              table={
                <div className="table-scroll">
                  <table className="w-full text-body-sm">
                    <thead>
                      <tr className="text-left text-caption text-ink-muted border-b border-surface-border">
                        <th className="py-2 pr-4 font-medium">Date</th>
                        <th className="py-2 pr-4 font-medium">Deposits</th>
                        <th className="py-2 font-medium">Withdrawals</th>
                      </tr>
                    </thead>
                    <tbody>
                      {depositsVsWithdrawals.map((group) => (
                        <tr key={group.label} className="border-b border-surface-border last:border-0">
                          <td className="py-2 pr-4 text-ink-secondary">{group.label}</td>
                          <td className="py-2 pr-4 font-medium text-ink-primary">{formatMoney(group.deposits, primaryAccount.currency)}</td>
                          <td className="py-2 font-medium text-ink-primary">{formatMoney(group.withdrawals, primaryAccount.currency)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              }
            />
          </div>
        </div>
      )}

      {/* Recent transactions */}
      <Card
        title="Recent transactions"
        action={
          primaryAccount && (
            <Link to="/transactions" className="text-body-sm font-medium text-brand-primary hover:underline">
              View all
            </Link>
          )
        }
      >
        <TransactionTable
          transactions={recentTransactions}
          loading={loading || transactionsLoading}
          emptyTitle="No recent activity"
          emptyDescription="Deposits and withdrawals on your primary account will show up here."
        />
      </Card>
    </div>
  );
}
