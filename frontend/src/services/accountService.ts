import { accountApiClient } from "./apiClient";
import type {
  Account,
  AccountCreateRequest,
  AmountRequest,
  Balance,
  ResolveRecipientRequest,
  ResolveRecipientResponse,
  TransferRequest,
  TransferResponse,
} from "@/types/account";

export const accountService = {
  async getAccount(id: string): Promise<Account> {
    const { data } = await accountApiClient.get<Account>(`/api/v1/accounts/${id}`);
    return data;
  },

  async getAccountsByCustomer(customerId: string): Promise<Account[]> {
    const { data } = await accountApiClient.get<Account[]>(`/api/v1/accounts/customer/${customerId}`);
    return data;
  },

  async getBalance(id: string): Promise<Balance> {
    const { data } = await accountApiClient.get<Balance>(`/api/v1/accounts/${id}/balance`);
    return data;
  },

  async createAccount(request: AccountCreateRequest): Promise<Account> {
    const { data } = await accountApiClient.post<Account>("/api/v1/accounts", request);
    return data;
  },

  async deposit(id: string, request: AmountRequest): Promise<Account> {
    const { data } = await accountApiClient.post<Account>(`/api/v1/accounts/${id}/deposit`, request);
    return data;
  },

  async withdraw(id: string, request: AmountRequest): Promise<Account> {
    const { data } = await accountApiClient.post<Account>(`/api/v1/accounts/${id}/withdraw`, request);
    return data;
  },

  /** Atomic internal transfer (Phase 7A), resolving the destination by account number + IFSC server-side (Phase 8B, ADR-005). */
  async transfer(request: TransferRequest): Promise<TransferResponse> {
    const { data } = await accountApiClient.post<TransferResponse>("/api/v1/accounts/transfer", request);
    return data;
  },

  /** Verifies a recipient before committing to a transfer — see account-service's ResolveRecipientRequest/ADR-005. */
  async resolveRecipient(request: ResolveRecipientRequest): Promise<ResolveRecipientResponse> {
    const { data } = await accountApiClient.post<ResolveRecipientResponse>("/api/v1/accounts/resolve-recipient", request);
    return data;
  },
};
