import type { Transaction } from "@/types/transaction";

export type TransactionDirection = "IN" | "OUT" | "NEUTRAL";

/**
 * DEPOSIT/WITHDRAWAL are unambiguous. TRANSFER is not: transaction-service's
 * TransactionResponse has no direction field and no link between a
 * transfer's two ledger legs (see ADR-004's documented limitation) — each
 * leg is just a same-shaped row on its own account. account-service's
 * default per-leg description text ("Transfer to account <id>" / "Transfer
 * from account <id>") is the only signal available to tell them apart, and
 * it's ONLY present when the sender didn't supply a custom description —
 * if they did, both legs carry the identical custom text and direction
 * genuinely can't be determined from this API's current data model. That
 * ambiguous case is a real, honestly-reported gap, not something to guess
 * at — see docs/09-engineering-journal for the Phase 8 write-up recommending
 * the backend add a `direction`/counterparty field in a future phase.
 */
export function transactionDirection(transaction: Transaction): TransactionDirection {
  if (transaction.transactionType === "DEPOSIT") return "IN";
  if (transaction.transactionType === "WITHDRAWAL") return "OUT";

  const description = transaction.description ?? "";
  if (/^Transfer to account /i.test(description)) return "OUT";
  if (/^Transfer from account /i.test(description)) return "IN";
  return "NEUTRAL";
}

export function transactionSign(transaction: Transaction): "+" | "-" | "" {
  const direction = transactionDirection(transaction);
  return direction === "IN" ? "+" : direction === "OUT" ? "-" : "";
}

/** Display label — e.g. "TRANSFER IN" / "TRANSFER OUT" / "TRANSFER" (direction unknown). */
export function transactionTypeLabel(transaction: Transaction): string {
  if (transaction.transactionType !== "TRANSFER") return transaction.transactionType;
  const direction = transactionDirection(transaction);
  if (direction === "IN") return "TRANSFER IN";
  if (direction === "OUT") return "TRANSFER OUT";
  return "TRANSFER";
}

/** Signed amount delta for balance-reconstruction math (e.g. Dashboard's balance trend) — 0 when direction can't be determined. */
export function transactionSignedAmount(transaction: Transaction): number {
  const direction = transactionDirection(transaction);
  if (direction === "IN") return transaction.amount;
  if (direction === "OUT") return -transaction.amount;
  return 0;
}
