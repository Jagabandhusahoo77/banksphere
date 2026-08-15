import { useCallback } from "react";
import { beneficiaryService } from "@/services/beneficiaryService";
import { useAsync } from "./useAsync";

/** Follows the same fetcher/useAsync convention as useAccounts/useTransactions — see useAsync.ts. */
export function useBeneficiaries(enabled: boolean) {
  const fetcher = useCallback(() => beneficiaryService.getBeneficiaries(), []);

  const { data, loading, error, reload } = useAsync(enabled ? fetcher : null, [enabled]);

  return { beneficiaries: data ?? [], loading, error, reload };
}
