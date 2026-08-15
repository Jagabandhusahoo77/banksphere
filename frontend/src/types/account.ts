export type AccountType = "SAVINGS" | "CURRENT";
export type AccountStatus = "ACTIVE" | "INACTIVE" | "CLOSED";

export interface Account {
  id: string;
  customerId: string;
  accountNumber: string;
  /** Server-controlled, same value on every BankSphere account — no branch model exists yet. Never client-supplied. */
  ifsc: string;
  accountType: AccountType;
  balance: number;
  currency: string;
  status: AccountStatus;
  createdAt: string;
  updatedAt: string;
}

/**
 * No `customerId` field — this was a stale leftover from before Phase 3A
 * (when the backend still accepted a client-supplied owner); the backend's
 * real `AccountCreateRequest` has never had one since ADR-002, Decision 4
 * removed it. `accountNumber`/`ifsc` are never request fields either —
 * both are always server-generated.
 */
export interface AccountCreateRequest {
  accountType: AccountType;
  currency: string;
  initialDeposit?: number;
}

export interface AmountRequest {
  amount: number;
  description?: string;
}

export interface Balance {
  accountId: string;
  accountNumber: string;
  balance: number;
  currency: string;
  asOf: string;
}

/**
 * No `customerId` field — the source account's ownership is derived from
 * the JWT server-side. The destination is identified by
 * `destinationAccountNumber` + `destinationIfsc` — the business
 * identifiers a customer actually knows — never an internal account id
 * (Phase 8B, ADR-005; supersedes Phase 7A's original `destinationAccountId`
 * shape). See account-service's `TransferRequest`.
 *
 * Phase 9D — `stepUpChallengeId` is set only after the customer has
 * requested and verified a step-up OTP for this exact operation (see
 * `stepUpService`); omitted entirely on the first attempt. `idempotencyKey`
 * is a client-generated opaque string, stable across a retry of the SAME
 * logical transfer attempt (including the step-up retry) so a double-click
 * or network retry can never execute the transfer twice — see
 * `Transfer.tsx` and ADR-009's idempotency section.
 */
export interface TransferRequest {
  sourceAccountId: string;
  destinationAccountNumber: string;
  destinationIfsc: string;
  amount: number;
  description?: string;
  stepUpChallengeId?: string;
  idempotencyKey?: string;
}

/**
 * `transferId` is an account-service-local correlation id for this
 * response only — it is NOT a transaction-service `TXN-...` ledger
 * reference (the transfer produces two independent ledger rows, each
 * with its own separately generated reference). See ADR-004.
 * `destinationAccountId` is deliberately absent — see ADR-005; the
 * frontend never has, and never needs, the destination's internal id.
 */
export interface TransferResponse {
  transferId: string;
  sourceAccountId: string;
  destinationAccountNumber: string;
  destinationIfsc: string;
  amount: number;
  currency: string;
  status: string;
  createdAt: string;
}

/**
 * Verifies a transfer recipient by account number + IFSC before the
 * customer commits to a transfer (Phase 8B) — matches real bank "verify
 * payee" UX. See account-service's `ResolveRecipientRequest`/ADR-005.
 */
export interface ResolveRecipientRequest {
  accountNumber: string;
  ifsc: string;
}

/**
 * Deliberately minimal — no internal account id, no customerId, no
 * balance (ADR-005). `bankName` is always `"BankSphere"` today (no
 * branch model exists yet).
 */
export interface ResolveRecipientResponse {
  accountNumber: string;
  ifsc: string;
  bankName: string;
}
