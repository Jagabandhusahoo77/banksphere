export function formatMoney(amount: string | number, currency: string): string {
  const value = typeof amount === "string" ? Number(amount) : amount;
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);
}

/**
 * INR-specific formatter using Indian digit grouping (lakh/crore, e.g.
 * ₹25,00,000) rather than formatMoney's Western grouping — used for the
 * fictional demo card/loan/deposit content, which is explicitly
 * INR-denominated, not for real backend account balances (those use
 * formatMoney with whatever currency the account actually holds).
 */
export function formatINR(amount: number): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(amount);
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

export function timeOfDayGreeting(date: Date = new Date()): string {
  const hour = date.getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}
