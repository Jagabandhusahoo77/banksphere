import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { employeeAuthService } from "@/services/employeeAuthService";
import { AUTH_CLEARED_EVENT, clearStoredAuth, getStoredAuth, isTokenExpired, setStoredAuth } from "@/services/tokenStorage";
import type { AuthenticatedEmployee, EmployeeLoginResponse } from "@/types/employee";

interface AuthContextValue {
  employee: AuthenticatedEmployee | null;
  isAuthenticated: boolean;
  /**
   * The only thing screens should ever branch rendering on. This mirrors —
   * never replaces — the backend's own @PreAuthorize checks: hiding a nav
   * link or a button here is a UX nicety, not a security boundary. See
   * ADR-006, Decision 4.
   */
  hasPermission: (permission: string) => boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function toAuthenticatedEmployee(response: EmployeeLoginResponse): AuthenticatedEmployee {
  return {
    id: response.employee.id,
    employeeNumber: response.employee.employeeNumber,
    username: response.employee.username,
    firstName: response.employee.firstName,
    lastName: response.employee.lastName,
    email: response.employee.email,
    status: response.employee.status,
    roles: response.roles,
    permissions: response.permissions,
    branch: response.branch,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [employee, setEmployee] = useState<AuthenticatedEmployee | null>(() => {
    const stored = getStoredAuth();
    if (!stored || isTokenExpired(stored.accessToken)) return null;
    return stored.employee;
  });

  useEffect(() => {
    const handleAuthCleared = () => setEmployee(null);
    window.addEventListener(AUTH_CLEARED_EVENT, handleAuthCleared);
    return () => window.removeEventListener(AUTH_CLEARED_EVENT, handleAuthCleared);
  }, []);

  const login = async (username: string, password: string) => {
    const response = await employeeAuthService.login({ username, password });
    const authenticatedEmployee = toAuthenticatedEmployee(response);
    setStoredAuth({ accessToken: response.accessToken, employee: authenticatedEmployee });
    setEmployee(authenticatedEmployee);
  };

  const logout = () => {
    // Stateless JWT, same as the customer portal — there is no server-side
    // session to invalidate; the token remains cryptographically valid
    // until it naturally expires (30 min default). See
    // docs/security/authentication.md.
    clearStoredAuth();
    setEmployee(null);
  };

  const hasPermission = (permission: string) => employee?.permissions.includes(permission) ?? false;

  return (
    <AuthContext.Provider value={{ employee, isAuthenticated: employee !== null, hasPermission, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within an AuthProvider");
  return context;
}
