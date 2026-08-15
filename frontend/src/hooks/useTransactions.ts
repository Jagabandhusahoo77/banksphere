import { useCallback } from "react";
import { transactionService } from "@/services/transactionService";
import { useAsync } from "./useAsync";

export function useTransactions(accountId: string | null, page: number, size: number) {
  const fetcher = useCallback(
    () => (accountId ? transactionService.getTransactionsByAccount(accountId, page, size) : Promise.reject()),
    [accountId, page, size],
  );

  const { data, loading, error, reload } = useAsync(accountId ? fetcher : null, [accountId, page, size]);

  return {
    transactions: data?.content ?? [],
    totalPages: data?.totalPages ?? 0,
    totalElements: data?.totalElements ?? 0,
    loading,
    error,
    reload,
  };
}
