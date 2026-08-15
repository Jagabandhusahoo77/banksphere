import { useCallback, useEffect, useState } from "react";
import { kycService } from "@/services/kycService";
import { ApiError } from "@/utils/apiError";
import type { KycApplication } from "@/types/kyc";

interface KycApplicationState {
  application: KycApplication | null;
  loading: boolean;
  error: string | null;
}

/**
 * A dedicated hook rather than a thin `useAsync` wrapper (unlike
 * useBeneficiaries/useAccounts): a 404 from `GET /api/v1/kyc/applications/me`
 * means "this customer has never started KYC," a normal, common state —
 * not a fetch failure the UI should render as an error. Every other
 * status still surfaces as a real error via `getFriendlyErrorMessage` at
 * the call site.
 */
export function useKycApplication() {
  const [state, setState] = useState<KycApplicationState>({ application: null, loading: true, error: null });
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setState((current) => ({ ...current, loading: true, error: null }));

    kycService
      .getMyApplication()
      .then((application) => {
        if (!cancelled) setState({ application, loading: false, error: null });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 404) {
          setState({ application: null, loading: false, error: null });
          return;
        }
        setState({
          application: null,
          loading: false,
          error: err instanceof Error ? err.message : "Something went wrong",
        });
      });

    return () => {
      cancelled = true;
    };
  }, [reloadToken]);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);

  return { ...state, reload };
}
