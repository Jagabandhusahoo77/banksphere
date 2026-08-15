import type { AuthenticatedEmployee } from "@/types/employee";

/**
 * Deliberately a DIFFERENT localStorage key from the customer portal's
 * "banksphere.auth" — the two portals are separate applications (see
 * ADR-006) and, even though browsers already isolate localStorage per
 * origin so this wouldn't collide in practice, a distinct key keeps that
 * separation explicit rather than relying on origin isolation alone. Same
 * token-handling trade-off as the customer portal (see
 * docs/security/authentication.md): localStorage, plain JSON, never in a
 * URL/query string/log.
 */
const STORAGE_KEY = "banksphere.employee.auth";

export const AUTH_CLEARED_EVENT = "banksphere-employee:auth-cleared";

export interface StoredAuth {
  accessToken: string;
  employee: AuthenticatedEmployee;
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

/** Same client-side-only expiry check as the customer portal — see tokenStorage.ts there for the full rationale. */
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
