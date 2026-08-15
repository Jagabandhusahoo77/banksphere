import { useCallback, useEffect, useState } from "react";

interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}

/**
 * Shared fetch/loading/error/cancelled-on-unmount pattern that used to be
 * copy-pasted independently in Dashboard, Accounts, and Transactions (see
 * the Phase 2 frontend discovery report). `fetcher` is re-run whenever
 * `deps` changes; pass `null` from the caller when there's nothing to fetch
 * yet (e.g. no customerId), and this hook skips the call entirely.
 */
export function useAsync<T>(
  fetcher: (() => Promise<T>) | null,
  deps: unknown[],
): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ data: null, loading: fetcher !== null, error: null });
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    if (!fetcher) {
      setState({ data: null, loading: false, error: null });
      return;
    }

    let cancelled = false;
    setState((current) => ({ ...current, loading: true, error: null }));

    fetcher()
      .then((data) => {
        if (!cancelled) setState({ data, loading: false, error: null });
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({ data: null, loading: false, error: err instanceof Error ? err.message : "Something went wrong" });
        }
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken]);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);

  return { ...state, reload };
}
