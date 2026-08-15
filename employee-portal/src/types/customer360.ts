import type { AccountSummary } from "./operations";
import type { DocumentType, KycStatus } from "./kyc";

/**
 * Mirrors employee-service's Customer360Section<T> exactly — `available`
 * is a permission-based gate (the caller's own JWT authorities), `data`
 * being null/empty with `available: true` is a different, unrelated
 * state meaning "you can see this, there's just nothing here yet." See
 * ADR-008.
 */
export interface Customer360Section<T> {
  available: boolean;
  unavailableReason: string | null;
  data: T | null;
}

export interface CustomerProfile {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  status: string;
  createdAt: string;
}

export interface TransactionSummary {
  id: string;
  transactionReference: string;
  accountId: string;
  transactionType: string;
  amount: number;
  currency: string;
  status: string;
  description: string;
  createdAt: string;
}

export interface BeneficiarySummary {
  id: string;
  beneficiaryName: string;
  accountNumber: string;
  ifsc: string;
  bankName: string;
  nickname: string | null;
  status: string;
}

export interface KycSummary {
  id: string;
  status: KycStatus;
  submittedAt: string | null;
  reviewedAt: string | null;
  reviewReason: string | null;
  missingDocumentTypes: DocumentType[];
  documents: unknown[];
}

export interface Customer360Response {
  customerId: string;
  customer: Customer360Section<CustomerProfile>;
  accounts: Customer360Section<AccountSummary[]>;
  transactions: Customer360Section<TransactionSummary[]>;
  beneficiaries: Customer360Section<BeneficiarySummary[]>;
  kyc: Customer360Section<KycSummary>;
  unavailableCapabilities: string[];
}
