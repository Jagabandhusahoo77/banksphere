import { useCallback } from "react";
import { accountService } from "@/services/accountService";
import { useAsync } from "./useAsync";

export function useAccounts(customerId: string | null) {
  const fetcher = useCallback(
    () => (customerId ? accountService.getAccountsByCustomer(customerId) : Promise.reject()),
    [customerId],
  );

  const { data, loading, error, reload } = useAsync(customerId ? fetcher : null, [customerId]);

  return { accounts: data ?? [], loading, error, reload };
}
