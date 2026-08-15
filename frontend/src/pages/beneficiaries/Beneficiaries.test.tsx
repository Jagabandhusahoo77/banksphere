import { describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/testUtils";
import { ApiError } from "@/utils/apiError";
import Beneficiaries from "./Beneficiaries";
import type { Beneficiary } from "@/types/beneficiary";

const getBeneficiaries = vi.fn();
const createBeneficiary = vi.fn();
vi.mock("@/services/beneficiaryService", () => ({
  beneficiaryService: {
    getBeneficiaries: (...args: unknown[]) => getBeneficiaries(...args),
    createBeneficiary: (...args: unknown[]) => createBeneficiary(...args),
    updateBeneficiary: vi.fn(),
    deactivateBeneficiary: vi.fn(),
  },
}));

const beneficiary: Beneficiary = {
  id: "ben-1",
  customerId: "customer-1",
  beneficiaryName: "Jane Doe",
  accountNumber: "987654321012",
  ifsc: "BANK0001234",
  bankName: "Test Bank",
  nickname: null,
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("Beneficiaries page", () => {
  it("loads and displays real beneficiaries from beneficiary-service", async () => {
    getBeneficiaries.mockResolvedValueOnce([beneficiary]);

    renderWithProviders(<Beneficiaries />);

    await waitFor(() => expect(getBeneficiaries).toHaveBeenCalled());
    expect(await screen.findByText("Jane Doe")).toBeInTheDocument();
    expect(screen.getByText("Test Bank")).toBeInTheDocument();
  });

  it("shows an empty state when there are genuinely no beneficiaries", async () => {
    getBeneficiaries.mockResolvedValueOnce([]);

    renderWithProviders(<Beneficiaries />);

    expect(await screen.findByText("No beneficiaries yet")).toBeInTheDocument();
  });

  it("creates a beneficiary through the real API after client-side validation passes", async () => {
    // Resolves both the initial load AND the reload() the page triggers
    // after a successful create — leaving the second call unmocked would
    // make that reload's fetcher() call return undefined.
    getBeneficiaries.mockResolvedValue([]);
    createBeneficiary.mockResolvedValueOnce({ ...beneficiary, id: "ben-2" });

    renderWithProviders(<Beneficiaries />);
    await screen.findByText("No beneficiaries yet");

    await userEvent.click(screen.getAllByRole("button", { name: "Add beneficiary" })[0]);

    await userEvent.type(screen.getByLabelText("Beneficiary name"), "John Smith");
    await userEvent.type(screen.getByLabelText("Account number"), "123456789012");
    await userEvent.type(screen.getByLabelText("IFSC code"), "bank0001234");
    await userEvent.type(screen.getByLabelText("Bank name"), "Some Bank");

    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Add beneficiary" }));

    await waitFor(() =>
      expect(createBeneficiary).toHaveBeenCalledWith({
        beneficiaryName: "John Smith",
        accountNumber: "123456789012",
        ifsc: "BANK0001234",
        bankName: "Some Bank",
        nickname: undefined,
      }),
    );

    // Wait for the post-create reload() to actually settle before the test
    // ends, so no in-flight fetch/state-update crosses into the next
    // test's unmount/cleanup.
    await waitFor(() => expect(getBeneficiaries).toHaveBeenCalledTimes(2));
  });

  it("blocks submission and shows field errors for an invalid IFSC, without calling the API", async () => {
    getBeneficiaries.mockResolvedValueOnce([]);

    renderWithProviders(<Beneficiaries />);
    await screen.findByText("No beneficiaries yet");

    await userEvent.click(screen.getAllByRole("button", { name: "Add beneficiary" })[0]);
    await userEvent.type(screen.getByLabelText("Beneficiary name"), "John Smith");
    await userEvent.type(screen.getByLabelText("Account number"), "123456789012");
    await userEvent.type(screen.getByLabelText("IFSC code"), "NOTVALID");
    await userEvent.type(screen.getByLabelText("Bank name"), "Some Bank");

    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Add beneficiary" }));

    expect(await screen.findByText(/valid 11-character IFSC/)).toBeInTheDocument();
    expect(createBeneficiary).not.toHaveBeenCalled();
  });

  it("shows a friendly message when the backend rejects a duplicate beneficiary (409)", async () => {
    getBeneficiaries.mockResolvedValueOnce([]);
    createBeneficiary.mockRejectedValueOnce(new ApiError("Duplicate active beneficiary", 409));

    renderWithProviders(<Beneficiaries />);
    await screen.findByText("No beneficiaries yet");

    await userEvent.click(screen.getAllByRole("button", { name: "Add beneficiary" })[0]);
    await userEvent.type(screen.getByLabelText("Beneficiary name"), "John Smith");
    await userEvent.type(screen.getByLabelText("Account number"), "123456789012");
    await userEvent.type(screen.getByLabelText("IFSC code"), "BANK0001234");
    await userEvent.type(screen.getByLabelText("Bank name"), "Some Bank");
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Add beneficiary" }));

    expect(await screen.findByText(/already have an active beneficiary/)).toBeInTheDocument();
  });
});
