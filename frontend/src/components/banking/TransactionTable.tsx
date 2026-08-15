import type { Transaction } from "@/types/transaction";
import TransactionRow from "./TransactionRow";
import EmptyState from "@/components/common/EmptyState";
import Skeleton from "@/components/common/Skeleton";

interface TransactionTableProps {
  transactions: Transaction[];
  loading?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
}

const COLUMNS = ["Reference", "Date", "Type", "Description", "Status", "Amount"];

export default function TransactionTable({
  transactions,
  loading = false,
  emptyTitle = "No transactions yet",
  emptyDescription = "Once there's activity on this account, it will show up here.",
}: TransactionTableProps) {
  if (!loading && transactions.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <div className="table-scroll">
      <table className="w-full min-w-[640px] text-left border-collapse">
        <thead>
          <tr className="border-b border-surface-border">
            {COLUMNS.map((column) => (
              <th
                key={column}
                className={`pb-2.5 pr-4 text-label text-ink-muted font-medium ${column === "Amount" ? "text-right" : ""}`}
              >
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading
            ? Array.from({ length: 4 }).map((_, i) => (
                <tr key={i} className="border-b border-surface-border last:border-0">
                  <td colSpan={COLUMNS.length} className="py-3">
                    <Skeleton className="h-4 w-full" />
                  </td>
                </tr>
              ))
            : transactions.map((transaction) => <TransactionRow key={transaction.id} transaction={transaction} />)}
        </tbody>
      </table>
    </div>
  );
}
