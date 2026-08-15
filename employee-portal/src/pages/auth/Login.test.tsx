import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import Login from "./Login";
import { ApiError } from "@/utils/apiError";

const mockLogin = vi.fn();
const mockUseAuth = vi.fn();
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/profile" element={<div>Profile page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("Login", () => {
  it("disables the submit button until both username and password are filled", async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, login: mockLogin });
    const user = userEvent.setup();
    renderLogin();

    const submit = screen.getByRole("button", { name: "Sign in" });
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText("Username"), "jane.teller");
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText("Password"), "correct-password");
    expect(submit).toBeEnabled();
  });

  it("submits the entered credentials and navigates to /profile on success", async () => {
    mockLogin.mockResolvedValue(undefined);
    mockUseAuth.mockReturnValue({ isAuthenticated: false, login: mockLogin });
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText("Username"), "jane.teller");
    await user.type(screen.getByLabelText("Password"), "correct-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(mockLogin).toHaveBeenCalledWith("jane.teller", "correct-password"));
    await waitFor(() => expect(screen.getByText("Profile page")).toBeInTheDocument());
  });

  it("shows the backend's generic invalid-credentials message on failure, without guessing which check failed", async () => {
    mockLogin.mockRejectedValue(new ApiError("Invalid username or password", 401));
    mockUseAuth.mockReturnValue({ isAuthenticated: false, login: mockLogin });
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText("Username"), "jane.teller");
    await user.type(screen.getByLabelText("Password"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid username or password.");
  });

  it("redirects to /profile immediately when already authenticated", () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true, login: mockLogin });
    renderLogin();

    expect(screen.getByText("Profile page")).toBeInTheDocument();
  });
});
