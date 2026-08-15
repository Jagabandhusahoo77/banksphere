import { useCallback } from "react";
import { customerService } from "@/services/customerService";
import { useAsync } from "./useAsync";

export function useCustomer(customerId: string | null) {
  const fetcher = useCallback(
    () => (customerId ? customerService.getCustomer(customerId) : Promise.reject()),
    [customerId],
  );

  const { data: customer, loading, error, reload } = useAsync(customerId ? fetcher : null, [customerId]);

  return { customer, loading, error, reload };
}
