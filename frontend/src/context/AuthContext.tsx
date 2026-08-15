import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { authService } from "@/services/authService";
import { AUTH_CLEARED_EVENT, clearStoredAuth, getStoredAuth, isTokenExpired, setStoredAuth } from "@/services/tokenStorage";
import type { AuthenticatedCustomer, RegisterRequest } from "@/types/auth";

interface AuthContextValue {
  customer: AuthenticatedCustomer | null;
  /** Derived from `customer.id` — kept so existing hooks (useCustomer(customerId), useAccounts(customerId)) don't need to change. */
  customerId: string | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  /** Does not auto-login, per spec — caller redirects to /login after this resolves. */
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
  /** Phase 9D, step 1 of OTP login — returns the challengeId the OTP screen needs for verifyOtp. Never reveals whether `identifier` matched a real customer. */
  requestOtp: (identifier: string) => Promise<{ challengeId: string }>;
  /** Phase 9D, step 2 of OTP login — same effect as a successful `login()` (stores the session, updates `customer`). */
  verifyOtp: (challengeId: string, otp: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [customer, setCustomer] = useState<AuthenticatedCustomer | null>(() => {
    const stored = getStoredAuth();
    if (!stored || isTokenExpired(stored.accessToken)) return null;
    return stored.customer;
  });

  // Lets apiClient's response interceptor force a logout (e.g. on a 401
  // from an expired/invalid token) and have this context's state follow,
  // without prop-drilling a setter down into a non-React module.
  useEffect(() => {
    const handleAuthCleared = () => setCustomer(null);
    window.addEventListener(AUTH_CLEARED_EVENT, handleAuthCleared);
    return () => window.removeEventListener(AUTH_CLEARED_EVENT, handleAuthCleared);
  }, []);

  const login = async (email: string, password: string) => {
    const response = await authService.login({ email, password });
    setStoredAuth({ accessToken: response.accessToken, customer: response.customer });
    setCustomer(response.customer);
  };

  const register = async (request: RegisterRequest) => {
    await authService.register(request);
  };

  const requestOtp = async (identifier: string) => {
    const response = await authService.requestOtp(identifier);
    return { challengeId: response.challengeId };
  };

  const verifyOtp = async (challengeId: string, otp: string) => {
    const response = await authService.verifyOtp(challengeId, otp);
    setStoredAuth({ accessToken: response.accessToken, customer: response.customer });
    setCustomer(response.customer);
  };

  const logout = () => {
    clearStoredAuth();
    setCustomer(null);
    // Logout is a documented no-op server-side (stateless JWT, nothing to
    // revoke) — fire-and-forget, the local session is already cleared.
    void authService.logout();
  };

  return (
    <AuthContext.Provider
      value={{
        customer,
        customerId: customer?.id ?? null,
        isAuthenticated: customer !== null,
        login,
        register,
        logout,
        requestOtp,
        verifyOtp,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
