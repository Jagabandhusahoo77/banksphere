import { customerApiClient } from "./apiClient";
import type {
  AuthResponse,
  AuthenticatedCustomer,
  DevOtpInboxEntry,
  LoginRequest,
  OtpRequestResponse,
  RegisterRequest,
} from "@/types/auth";

export const authService = {
  async register(request: RegisterRequest): Promise<AuthenticatedCustomer> {
    const { data } = await customerApiClient.post<AuthenticatedCustomer>("/api/v1/auth/register", request);
    return data;
  },

  async login(request: LoginRequest): Promise<AuthResponse> {
    const { data } = await customerApiClient.post<AuthResponse>("/api/v1/auth/login", request);
    return data;
  },

  async logout(): Promise<void> {
    await customerApiClient.post("/api/v1/auth/logout");
  },

  async getCurrentCustomer(): Promise<AuthenticatedCustomer> {
    const { data } = await customerApiClient.get<AuthenticatedCustomer>("/api/v1/auth/me");
    return data;
  },

  /** Phase 9D — step 1 of OTP login: `identifier` is an email or phone number. Response is deliberately generic — see its own type's javadoc. */
  async requestOtp(identifier: string): Promise<OtpRequestResponse> {
    const { data } = await customerApiClient.post<OtpRequestResponse>("/api/v1/auth/otp/request", { identifier });
    return data;
  },

  /** Phase 9D — step 2 of OTP login: on success, issues the same AuthResponse shape as password login (plus a refresh-token cookie the browser handles automatically). */
  async verifyOtp(challengeId: string, otp: string): Promise<AuthResponse> {
    const { data } = await customerApiClient.post<AuthResponse>("/api/v1/auth/otp/verify", { challengeId, otp });
    return data;
  },

  /**
   * Development-only convenience — see DevOtpInboxEntry's own javadoc.
   * Only ever called from a component that has already gated itself
   * behind `import.meta.env.DEV`; the backend route independently 404s
   * outside a dev profile regardless (see ADR-009), so this call simply
   * fails harmlessly if invoked against a non-dev deployment.
   */
  async getDevOtpInbox(): Promise<DevOtpInboxEntry[]> {
    const { data } = await customerApiClient.get<DevOtpInboxEntry[]>("/api/v1/auth/dev/otp-inbox");
    return data;
  },
};
