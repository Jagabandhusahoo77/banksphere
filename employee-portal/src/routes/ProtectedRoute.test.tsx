import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render } from "@testing-library/react";
import ProtectedRoute from "./ProtectedRoute";

const mockUseAuth = vi.fn();
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderProtected(initialAuthenticated: boolean) {
  mockUseAuth.mockReturnValue({ isAuthenticated: initialAuthenticated });
  return render(
    <MemoryRouter initialEntries={["/profile"]}>
      <Routes>
        <Route path="/login" element={<div>Login page</div>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/profile" element={<div>Profile page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("redirects to /login when not authenticated", () => {
    renderProtected(false);
    expect(screen.getByText("Login page")).toBeInTheDocument();
    expect(screen.queryByText("Profile page")).not.toBeInTheDocument();
  });

  it("renders the protected content when authenticated", () => {
    renderProtected(true);
    expect(screen.getByText("Profile page")).toBeInTheDocument();
  });
});
