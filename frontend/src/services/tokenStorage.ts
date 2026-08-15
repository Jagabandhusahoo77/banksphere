import type { AuthenticatedCustomer } from "@/types/auth";

/**
 * Token storage strategy: localStorage, plain JSON — see
 * docs/security/authentication.md ("Frontend Token Handling") for the
 * full trade-off discussion. Short version: localStorage is readable by
 * any JS running on the page, so an XSS vulnerability elsewhere in the
 * app could exfiltrate the token — this is a real, accepted risk for a
 * portfolio/demo phase, not a claim that this is production-grade token
 * handling. The token is never placed in a URL, query string, or log.
 */
const STORAGE_KEY = "banksphere.auth";

/** Dispatched whenever the stored auth is cleared — lets AuthContext react to a forced logout (e.g. a 401 from apiClient) without prop-drilling. */
export const AUTH_CLEARED_EVENT = "banksphere:auth-cleared";

export interface StoredAuth {
  accessToken: string;
  customer: AuthenticatedCustomer;
}

export function getStoredAuth(): StoredAuth | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredAuth;
  } catch {
    return null;
  }
}

export function setStoredAuth(auth: StoredAuth): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
}

export function clearStoredAuth(): void {
  localStorage.removeItem(STORAGE_KEY);
  window.dispatchEvent(new Event(AUTH_CLEARED_EVENT));
}

export function getToken(): string | null {
  return getStoredAuth()?.accessToken ?? null;
}

/**
 * Decodes the JWT payload (base64) to read its `exp` claim — no signature
 * verification, since the frontend isn't the trust boundary (the backend
 * always re-validates the signature on every request). This is purely so
 * the UI doesn't show a "logged in" state for a token that's certainly
 * already expired, avoiding a guaranteed-to-fail request and a confusing
 * flash of authenticated UI before the redirect to /login.
 */
export function isTokenExpired(token: string): boolean {
  try {
    const payloadSegment = token.split(".")[1];
    const payload = JSON.parse(atob(payloadSegment)) as { exp?: number };
    if (typeof payload.exp !== "number") return false;
    return Date.now() >= payload.exp * 1000;
  } catch {
    return true;
  }
}
