import { describe, it, expect, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AuthProvider, useAuth } from "./AuthContext";
import { renderWithProviders } from "@/test/testUtils";
import { employeeAuthService } from "@/services/employeeAuthService";
import { clearStoredAuth } from "@/services/tokenStorage";

vi.mock("@/services/employeeAuthService", () => ({
  employeeAuthService: {
    login: vi.fn(),
    getCurrentEmployee: vi.fn(),
  },
}));

function TestConsumer() {
  const { employee, isAuthenticated, hasPermission, login, logout } = useAuth();
  return (
    <div>
      <p data-testid="authenticated">{String(isAuthenticated)}</p>
      <p data-testid="employee-name">{employee ? `${employee.firstName} ${employee.lastName}` : "none"}</p>
      <p data-testid="has-employee-manage">{String(hasPermission("EMPLOYEE_MANAGE"))}</p>
      <button onClick={() => login("jane.teller", "correct-password")}>Login</button>
      <button onClick={logout}>Logout</button>
    </div>
  );
}

const loginResponse = {
  accessToken: "a-real-jwt",
  tokenType: "Bearer",
  expiresIn: 1800,
  employee: {
    id: "emp-1",
    employeeNumber: "EMP000123",
    username: "jane.teller",
    firstName: "Jane",
    lastName: "Teller",
    email: "jane.teller@banksphere.example",
    status: "ACTIVE" as const,
  },
  roles: ["TELLER"],
  permissions: ["CUSTOMER_VIEW", "ACCOUNT_VIEW", "CASH_DEPOSIT", "CASH_WITHDRAWAL"],
  branch: { id: "branch-1", branchCode: "HQ001", branchName: "Head Office", ifsc: "BANK0000001" },
};

describe("AuthContext", () => {
  it("starts unauthenticated when nothing is stored", () => {
    clearStoredAuth();
    renderWithProviders(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );
    expect(screen.getByTestId("authenticated")).toHaveTextContent("false");
  });

  it("login stores the employee and marks the context authenticated", async () => {
    clearStoredAuth();
    vi.mocked(employeeAuthService.login).mockResolvedValue(loginResponse);
    const user = userEvent.setup();

    renderWithProviders(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Login" }));

    await waitFor(() => expect(screen.getByTestId("authenticated")).toHaveTextContent("true"));
    expect(screen.getByTestId("employee-name")).toHaveTextContent("Jane Teller");
    expect(employeeAuthService.login).toHaveBeenCalledWith({ username: "jane.teller", password: "correct-password" });
  });

  it("hasPermission reflects the permissions granted at login, never a hardcoded assumption from the role name", async () => {
    clearStoredAuth();
    vi.mocked(employeeAuthService.login).mockResolvedValue(loginResponse);
    const user = userEvent.setup();

    renderWithProviders(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Login" }));

    await waitFor(() => expect(screen.getByTestId("authenticated")).toHaveTextContent("true"));
    // TELLER does not carry EMPLOYEE_MANAGE in RolePermissions — the
    // context must reflect that, not assume every logged-in employee has it.
    expect(screen.getByTestId("has-employee-manage")).toHaveTextContent("false");
  });

  it("logout clears the stored session and returns the context to unauthenticated", async () => {
    clearStoredAuth();
    vi.mocked(employeeAuthService.login).mockResolvedValue(loginResponse);
    const user = userEvent.setup();

    renderWithProviders(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Login" }));
    await waitFor(() => expect(screen.getByTestId("authenticated")).toHaveTextContent("true"));

    await user.click(screen.getByRole("button", { name: "Logout" }));
    expect(screen.getByTestId("authenticated")).toHaveTextContent("false");
    expect(screen.getByTestId("employee-name")).toHaveTextContent("none");
  });
});
