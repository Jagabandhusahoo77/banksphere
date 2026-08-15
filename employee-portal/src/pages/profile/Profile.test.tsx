import { describe, it, expect, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import Profile from "./Profile";
import { renderWithProviders } from "@/test/testUtils";
import { employeeAuthService } from "@/services/employeeAuthService";
import { ApiError } from "@/utils/apiError";

vi.mock("@/services/employeeAuthService", () => ({
  employeeAuthService: {
    getCurrentEmployee: vi.fn(),
  },
}));

const sampleProfile = {
  id: "emp-1",
  employeeNumber: "EMP000123",
  username: "jane.teller",
  firstName: "Jane",
  lastName: "Teller",
  email: "jane.teller@banksphere.example",
  roles: ["TELLER"],
  permissions: ["CUSTOMER_VIEW", "ACCOUNT_VIEW", "CASH_DEPOSIT", "CASH_WITHDRAWAL"],
  branch: { id: "branch-1", branchCode: "HQ001", branchName: "Head Office", ifsc: "BANK0000001" },
  status: "ACTIVE" as const,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("Profile", () => {
  it("fetches and displays the authenticated employee's real profile from GET /me — never data reused from login alone", async () => {
    vi.mocked(employeeAuthService.getCurrentEmployee).mockResolvedValue(sampleProfile);
    renderWithProviders(<Profile />);

    await waitFor(() => expect(screen.getByText("Jane Teller")).toBeInTheDocument());
    expect(screen.getByText("EMP000123")).toBeInTheDocument();
    expect(screen.getByText("Head Office")).toBeInTheDocument();
    expect(screen.getByText("BANK0000001")).toBeInTheDocument();
    expect(screen.getByText("TELLER")).toBeInTheDocument();
    expect(screen.getByText("CASH_DEPOSIT")).toBeInTheDocument();
    expect(employeeAuthService.getCurrentEmployee).toHaveBeenCalledTimes(1);
  });

  it("never renders a password or hash field even though none exists on the response type", async () => {
    vi.mocked(employeeAuthService.getCurrentEmployee).mockResolvedValue(sampleProfile);
    const { container } = renderWithProviders(<Profile />);

    await waitFor(() => expect(screen.getByText("Jane Teller")).toBeInTheDocument());
    expect(container.textContent?.toLowerCase()).not.toContain("password");
  });

  it("shows an error state, not a blank or fabricated profile, when the request fails", async () => {
    vi.mocked(employeeAuthService.getCurrentEmployee).mockRejectedValue(new ApiError("Something went wrong", 500));
    renderWithProviders(<Profile />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText("Jane Teller")).not.toBeInTheDocument();
  });
});
