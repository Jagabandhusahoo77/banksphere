import axios, { type AxiosInstance } from "axios";
import { clearStoredAuth, getToken } from "./tokenStorage";
import { ApiError } from "@/utils/apiError";
import { getRuntimeApiBaseUrl } from "@/config/runtimeConfig";

// Deployed environments route every backend service through the same
// gateway, so the runtime config's single API_BASE_URL — when present —
// takes priority over the VITE_*_SERVICE_URL build-time values, which
// stay meaningful only for local dev (each service on its own localhost
// port). See frontend/src/services/apiClient.ts's identical pattern.
const RUNTIME_API_BASE_URL = getRuntimeApiBaseUrl();

function createApiClient(baseURL: string): AxiosInstance {
  const client = axios.create({
    baseURL,
    headers: {
      "Content-Type": "application/json",
    },
  });

  client.interceptors.request.use((config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error?.response?.status === 401) {
        clearStoredAuth();
      }

      const message = error?.response?.data?.message ?? error?.message ?? "An unexpected error occurred";
      const status: number | undefined = error?.response?.status;
      const details: string[] | undefined = error?.response?.data?.details;
      return Promise.reject(new ApiError(message, status, details));
    },
  );

  return client;
}

export const employeeApiClient = createApiClient(
  RUNTIME_API_BASE_URL ?? import.meta.env.VITE_EMPLOYEE_SERVICE_URL ?? "http://localhost:8085",
);

// Phase 9C — KYC review endpoints are called directly (not proxied
// through employee-service, unlike Cash Operations) since kyc-service is
// its own bounded domain with a complete employee-facing API surface of
// its own. See ADR-008.
export const kycApiClient = createApiClient(
  RUNTIME_API_BASE_URL ?? import.meta.env.VITE_KYC_SERVICE_URL ?? "http://localhost:8086",
);
