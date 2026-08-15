import axios, { type AxiosInstance } from "axios";
import { clearStoredAuth, getStoredAuth, getToken, setStoredAuth } from "./tokenStorage";
import { ApiError } from "@/utils/apiError";
import type { AuthResponse } from "@/types/auth";

const CUSTOMER_SERVICE_URL = import.meta.env.VITE_CUSTOMER_SERVICE_URL ?? "http://localhost:8081";

/**
 * Phase 9D — a request to `/api/v1/auth/token/refresh` is the one call in
 * this app that must carry the HttpOnly refresh-token cookie
 * (`banksphere_refresh_token`, see customer-service's RefreshTokenCookies)
 * rather than a Bearer header, so it's issued as a bare axios call
 * outside the normal apiClient instances (which attach a Bearer token,
 * not cookies). At most one refresh is ever in flight at a time — every
 * caller within that window shares the same promise — so N requests that
 * all 401 at once because the access token just expired trigger exactly
 * one rotation, not N (which would race each other against the
 * single-use refresh-token-rotation backend, and every request after the
 * first would see reuse-detection revoke the whole session).
 */
let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<AuthResponse>(`${CUSTOMER_SERVICE_URL}/api/v1/auth/token/refresh`, {}, { withCredentials: true })
      .then(({ data }) => {
        const stored = getStoredAuth();
        setStoredAuth({ accessToken: data.accessToken, customer: stored?.customer ?? data.customer });
        return data.accessToken;
      })
      .catch(() => {
        clearStoredAuth();
        return null;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

/** Endpoints where a 401 means "these credentials were wrong," not "this session expired" — never worth a silent refresh attempt. */
const NEVER_REFRESH_FOR = ["/auth/login", "/auth/register", "/auth/otp/", "/auth/token/refresh"];

function createApiClient(baseURL: string, withCredentials = false): AxiosInstance {
  const client = axios.create({
    baseURL,
    withCredentials,
    headers: {
      "Content-Type": "application/json",
    },
  });

  // Attach the stored JWT to every request via the shared apiClient
  // abstraction — components and services never set this header
  // themselves. Safe to run unconditionally: before login, getToken()
  // is null and no header is added.
  client.interceptors.request.use((config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      const status: number | undefined = error?.response?.status;
      const requestUrl: string = error?.config?.url ?? "";
      const alreadyRetried = Boolean(error?.config?._bankspherRetried);
      const eligibleForRefresh =
        status === 401 && !alreadyRetried && getToken() && !NEVER_REFRESH_FOR.some((path) => requestUrl.includes(path));

      if (eligibleForRefresh) {
        const newAccessToken = await refreshAccessToken();
        if (newAccessToken) {
          error.config._bankspherRetried = true;
          error.config.headers.Authorization = `Bearer ${newAccessToken}`;
          return client(error.config);
        }
      }

      if (status === 401) {
        // Covers both "session expired / token invalid" for an
        // already-logged-in user and a failed login attempt itself —
        // either way there is no valid session, so clearing is correct.
        // AuthContext listens for this to update its own state.
        clearStoredAuth();
      }

      const message =
        error?.response?.data?.message ?? error?.message ?? "An unexpected error occurred";
      const details: string[] | undefined = error?.response?.data?.details;
      return Promise.reject(new ApiError(message, status, details));
    },
  );

  return client;
}

// The only client that ever sends/receives the HttpOnly refresh-token
// cookie — customer-service is the only service whose CorsConfig allows
// credentials (see ADR-009), and the cookie is path-scoped to
// /api/v1/auth there regardless.
export const customerApiClient = createApiClient(CUSTOMER_SERVICE_URL, true);

export const accountApiClient = createApiClient(
  import.meta.env.VITE_ACCOUNT_SERVICE_URL ?? "http://localhost:8082",
);

export const transactionApiClient = createApiClient(
  import.meta.env.VITE_TRANSACTION_SERVICE_URL ?? "http://localhost:8083",
);

export const beneficiaryApiClient = createApiClient(
  import.meta.env.VITE_BENEFICIARY_SERVICE_URL ?? "http://localhost:8084",
);

export const kycApiClient = createApiClient(
  import.meta.env.VITE_KYC_SERVICE_URL ?? "http://localhost:8086",
);
