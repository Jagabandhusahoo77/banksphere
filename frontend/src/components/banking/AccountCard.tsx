import { useState } from "react";
import { Link } from "react-router-dom";
import type { Account } from "@/types/account";
import Badge from "@/components/common/Badge";
import Button from "@/components/common/Button";
import Icon from "@/components/common/Icon";
import { accountTypeLabel, formatMoney, maskAccountNumber } from "@/utils/format";

const STATUS_TONE = {
  ACTIVE: "success",
  INACTIVE: "neutral",
  CLOSED: "error",
} as const;

export default function AccountCard({ account }: { account: Account }) {
  const [revealed, setRevealed] = useState(false);

  return (
    <div className="bg-white rounded-lg border border-surface-border shadow-elevation-1 p-5 sm:p-6">
      <div className="flex items-start justify-between mb-4">
        <div>
          <p className="text-label text-ink-secondary">{accountTypeLabel(account.accountType)}</p>
          <div className="flex items-center gap-2 mt-1">
            <span className="font-mono text-body-sm text-ink-primary">
              {revealed ? account.accountNumber : maskAccountNumber(account.accountNumber)}
            </span>
            <button
              type="button"
              onClick={() => setRevealed((value) => !value)}
              aria-label={revealed ? "Hide account number" : "Show account number"}
              className="text-ink-muted hover:text-ink-primary"
            >
              <Icon name={revealed ? "eye-off" : "eye"} size={16} />
            </button>
          </div>
        </div>
        <Badge tone={STATUS_TONE[account.status]}>{account.status}</Badge>
      </div>

      <p className="text-h2 text-ink-primary">{formatMoney(account.balance, account.currency)}</p>
      <p className="text-caption text-ink-muted mt-1">Available balance · {account.currency}</p>

      <div className="mt-5 flex gap-2">
        <Link to={`/accounts/${account.id}`} className="flex-1">
          <Button variant="outline" size="sm" fullWidth>
            Account details
          </Button>
        </Link>
        <Link to={`/transactions?accountId=${account.id}`} className="flex-1">
          <Button variant="ghost" size="sm" fullWidth icon="list">
            Transactions
          </Button>
        </Link>
      </div>
    </div>
  );
}
