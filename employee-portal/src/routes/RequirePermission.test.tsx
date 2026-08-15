import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import RequirePermission from "./RequirePermission";

const mockUseAuth = vi.fn();
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderGated(hasPermission: (permission: string) => boolean) {
  mockUseAuth.mockReturnValue({ hasPermission });
  return render(
    <MemoryRouter initialEntries={["/employees"]}>
      <Routes>
        <Route path="/unauthorized" element={<div>Unauthorized page</div>} />
        <Route element={<RequirePermission permission="EMPLOYEE_MANAGE" />}>
          <Route path="/employees" element={<div>Employee admin page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("RequirePermission", () => {
  it("redirects to /unauthorized when the employee lacks the required permission", () => {
    renderGated(() => false);
    expect(screen.getByText("Unauthorized page")).toBeInTheDocument();
    expect(screen.queryByText("Employee admin page")).not.toBeInTheDocument();
  });

  it("renders the gated content when the employee has the required permission", () => {
    renderGated((permission) => permission === "EMPLOYEE_MANAGE");
    expect(screen.getByText("Employee admin page")).toBeInTheDocument();
  });
});
