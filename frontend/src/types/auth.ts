export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  address: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthenticatedCustomer {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  customer: AuthenticatedCustomer;
}

/**
 * Phase 9D — OTP login. `identifier` is the customer's email or phone
 * (whichever they registered with); the response is deliberately generic
 * either way — see ADR-009's account-enumeration section — so the
 * frontend can never distinguish "sent" from "identifier didn't match
 * anyone" from the response alone.
 */
export interface OtpRequestResponse {
  message: string;
  challengeId: string;
}

/**
 * Bound to a specific transfer's exact fields at request time — see
 * OtpContextHasher/ADR-009. `amount` and `currency` here must match
 * exactly what the transfer actually executes with; the backend
 * re-verifies this itself, but the frontend must never let a user change
 * the amount/recipient after requesting step-up and still reuse the same
 * challenge.
 */
export interface StepUpTransferContext {
  sourceAccountId: string;
  destinationAccountNumber: string;
  destinationIfsc: string;
  amount: number;
  currency: string;
}

export interface StepUpRequestResponse {
  challengeId: string;
  expiresAt: string;
}

export interface StepUpVerifyResponse {
  verified: boolean;
  challengeId: string;
  expiresAt: string;
}

/**
 * Development-only — see customer-service's DevOtpInboxController. Carries
 * the real, plaintext OTP; only ever rendered behind `import.meta.env.DEV`
 * on this side, and the backend route itself does not exist at all
 * outside a dev profile (see ADR-009) — two independent gates, not one.
 */
export interface DevOtpInboxEntry {
  challengeId: string;
  identifier: string;
  purpose: string;
  otp: string;
  createdAt: string;
  expiresAt: string;
}
