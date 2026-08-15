import Icon, { type IconName } from "@/components/common/Icon";
import { formatMoney } from "@/utils/format";

interface BalanceCardProps {
  label: string;
  amount: number;
  currency: string;
  icon?: IconName;
  tone?: "primary" | "default";
  helperText?: string;
}

export default function BalanceCard({ label, amount, currency, icon = "wallet", tone = "default", helperText }: BalanceCardProps) {
  const isPrimary = tone === "primary";

  return (
    <div
      className={`rounded-lg p-5 sm:p-6 ${
        isPrimary
          ? "bg-gradient-to-br from-brand-primary to-brand-primary-dark text-white shadow-elevation-2"
          : "bg-white border border-surface-border shadow-elevation-1"
      }`}
    >
      <div className="flex items-center justify-between mb-4">
        <span className={`text-label ${isPrimary ? "text-white/75" : "text-ink-secondary"}`}>{label}</span>
        <span
          className={`flex items-center justify-center w-9 h-9 rounded-full ${
            isPrimary ? "bg-white/15" : "bg-brand-primary-light text-brand-primary"
          }`}
        >
          <Icon name={icon} size={18} />
        </span>
      </div>
      <p className={`text-h1 ${isPrimary ? "text-white" : "text-ink-primary"}`}>{formatMoney(amount, currency)}</p>
      {helperText && (
        <p className={`mt-1.5 text-body-sm ${isPrimary ? "text-white/70" : "text-ink-muted"}`}>{helperText}</p>
      )}
    </div>
  );
}
