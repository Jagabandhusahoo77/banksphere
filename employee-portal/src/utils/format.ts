/** Money values are always treated as opaque numbers for display only — never arithmetic client-side. See CLAUDE.md. */
export function formatMoney(amount: string | number, currency: string): string {
  const value = typeof amount === "string" ? Number(amount) : amount;
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);
}

export function formatDateTime(isoString: string): string {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(isoString));
}

export function maskAccountNumber(accountNumber: string): string {
  const visibleDigits = 4;
  if (accountNumber.length <= visibleDigits) return accountNumber;
  return `•••• ${accountNumber.slice(-visibleDigits)}`;
}

export function accountTypeLabel(accountType: string): string {
  return accountType === "SAVINGS" ? "Savings Account" : "Current Account";
}
