import axios, { type AxiosInstance } from "axios";
import { clearStoredAuth, getToken } from "./tokenStorage";
import { ApiError } from "@/utils/apiError";

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
  import.meta.env.VITE_EMPLOYEE_SERVICE_URL ?? "http://localhost:8085",
);

// Phase 9C — KYC review endpoints are called directly (not proxied
// through employee-service, unlike Cash Operations) since kyc-service is
// its own bounded domain with a complete employee-facing API surface of
// its own. See ADR-008.
export const kycApiClient = createApiClient(
  import.meta.env.VITE_KYC_SERVICE_URL ?? "http://localhost:8086",
);
