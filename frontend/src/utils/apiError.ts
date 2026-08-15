/**
 * Thrown by every request made through the shared `apiClient.ts` instances
 * in place of a plain `Error` — `message` is still always a plain string
 * (existing callers doing `err instanceof Error ? err.message : ...`
 * continue to work unchanged), but `status`/`details` are additionally
 * preserved from the backend's `ErrorResponse` so new callers can branch
 * on the actual HTTP status instead of only ever seeing the generic
 * top-level message. `status` is `undefined` for a genuine network
 * failure (request never reached a server to receive a response).
 */
export class ApiError extends Error {
  readonly status?: number;
  readonly details?: string[];

  constructor(message: string, status?: number, details?: string[]) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}

/**
 * Maps a caught error to consistent, user-friendly copy for the money-
 * moving flows (deposit/withdraw/transfer/beneficiary CRUD) per the
 * status-code conventions documented in docs/security and every backend
 * service's GlobalExceptionHandler. `overrides` lets a specific screen
 * substitute a more contextual message for a status this operation cares
 * about (e.g. 422 meaning "insufficient balance" on a transfer, vs 422
 * meaning "account not active" on a deposit) while still falling back to
 * the backend's own message when no override is given — the backend's
 * ErrorResponse.message is already human-readable copy (see e.g.
 * InsufficientBalanceException/DuplicateBeneficiaryException), not
 * internal detail, so it's safe to show as-is by default.
 */
export function getFriendlyErrorMessage(err: unknown, overrides: Partial<Record<number, string>> = {}): string {
  if (err instanceof ApiError) {
    if (err.status === undefined) {
      return "Couldn't reach the server. Check your connection and try again.";
    }
    if (overrides[err.status]) {
      return overrides[err.status]!;
    }
    switch (err.status) {
      case 401:
        return "Your session has expired. Please sign in again.";
      case 403:
        return "You are not authorized to perform this operation.";
      case 404:
        return "We couldn't find what you were looking for.";
      case 409:
        return "This was updated by another operation. Please refresh and try again.";
      case 422:
        return err.message || "This operation can't be completed right now.";
      case 500:
        return "Something went wrong on our end. Please try again shortly.";
      default:
        return err.message || "Something went wrong. Please try again.";
    }
  }

  return err instanceof Error ? err.message : "Something went wrong. Please try again.";
}
