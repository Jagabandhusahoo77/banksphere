export interface AccountSummary {
  id: string;
  accountNumber: string;
  accountType: string;
  balance: number;
  currency: string;
  status: string;
}

export interface CustomerSearchResponse {
  customerId: string;
  customerName: string;
  accounts: AccountSummary[];
}

export interface CashDepositRequest {
  accountId: string;
  amount: number;
  description?: string;
}

export interface CashDepositResponse {
  operationReference: string;
  accountId: string;
  accountNumber: string;
  newBalance: number;
  currency: string;
  transactionReference: string | null;
  status: string;
  performedBy: string;
  branchCode: string;
}

export interface CashDepositHistoryEntry {
  operationReference: string;
  customerName: string | null;
  accountNumber: string;
  amount: number;
  currency: string;
  status: string;
  transactionReference: string | null;
  createdAt: string;
}
