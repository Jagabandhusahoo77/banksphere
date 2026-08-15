import { transactionApiClient } from "./apiClient";
import type { PageResponse, Transaction } from "@/types/transaction";

export const transactionService = {
  async getTransaction(id: string): Promise<Transaction> {
    const { data } = await transactionApiClient.get<Transaction>(`/api/v1/transactions/${id}`);
    return data;
  },

  async getTransactionsByAccount(
    accountId: string,
    page = 0,
    size = 20,
  ): Promise<PageResponse<Transaction>> {
    const { data } = await transactionApiClient.get<PageResponse<Transaction>>(
      `/api/v1/transactions/account/${accountId}`,
      { params: { page, size } },
    );
    return data;
  },
};
