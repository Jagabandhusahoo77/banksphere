import type { Transaction } from "@/types/transaction";
import Badge from "@/components/common/Badge";
import Icon from "@/components/common/Icon";
import { formatDateTime, formatMoney } from "@/utils/format";
import { transactionDirection, transactionSign, transactionTypeLabel } from "@/utils/transactionDirection";

const STATUS_TONE = {
  COMPLETED: "success",
  PENDING: "warning",
  FAILED: "error",
} as const;

const DIRECTION_ICON = {
  IN: "arrow-down-left",
  OUT: "arrow-up-right",
  NEUTRAL: "arrow-right",
} as const;

export default function TransactionRow({ transaction }: { transaction: Transaction }) {
  const direction = transactionDirection(transaction);
  const sign = transactionSign(transaction);
  const amountClass = direction === "IN" ? "text-semantic-success" : "text-ink-primary";

  return (
    <tr className="border-b border-surface-border last:border-0">
      <td className="py-3 pr-4 font-mono text-caption text-ink-muted whitespace-nowrap">
        {transaction.transactionReference}
      </td>
      <td className="py-3 pr-4 text-body-sm text-ink-secondary whitespace-nowrap">
        {formatDateTime(transaction.createdAt)}
      </td>
      <td className="py-3 pr-4 text-body-sm text-ink-primary whitespace-nowrap">
        <span className="inline-flex items-center gap-1.5">
          <Icon name={DIRECTION_ICON[direction]} size={14} className="text-ink-muted" />
          {transactionTypeLabel(transaction)}
        </span>
      </td>
      <td className="py-3 pr-4 text-body-sm text-ink-secondary">{transaction.description ?? "—"}</td>
      <td className="py-3 pr-4">
        <Badge tone={STATUS_TONE[transaction.status]}>{transaction.status}</Badge>
      </td>
      <td className={`py-3 text-right text-body-sm font-medium whitespace-nowrap ${amountClass}`}>
        {sign}
        {formatMoney(transaction.amount, transaction.currency)}
      </td>
    </tr>
  );
}
