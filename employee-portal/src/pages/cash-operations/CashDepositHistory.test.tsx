import { describe, it, expect, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import CashDepositHistory from "./CashDepositHistory";
import { renderWithProviders } from "@/test/testUtils";
import { operationsService } from "@/services/operationsService";
import { ApiError } from "@/utils/apiError";

vi.mock("@/services/operationsService", () => ({
  operationsService: {
    customerSearchByAccountNumber: vi.fn(),
    customerSearchByCustomerId: vi.fn(),
    cashDeposit: vi.fn(),
    cashDepositHistory: vi.fn(),
  },
}));

describe("CashDepositHistory", () => {
  it("renders real entries returned by the backend, including a masked account number", async () => {
    vi.mocked(operationsService.cashDepositHistory).mockResolvedValue([
      {
        operationReference: "CD-0000000001",
        customerName: "John Smith",
        accountNumber: "617242043877",
        amount: 10000,
        currency: "INR",
        status: "COMPLETED",
        transactionReference: "TXN-ABC123",
        createdAt: "2026-08-13T10:42:00Z",
      },
    ]);
    renderWithProviders(<CashDepositHistory />);

    await waitFor(() => expect(screen.getByText("CD-0000000001")).toBeInTheDocument());
    expect(screen.getByText("John Smith")).toBeInTheDocument();
    expect(screen.getByText("•••• 3877")).toBeInTheDocument();
    expect(screen.getByText("COMPLETED")).toBeInTheDocument();
  });

  it("shows a real customer name as an em-dash placeholder, never a fabricated one, when it couldn't be resolved", async () => {
    vi.mocked(operationsService.cashDepositHistory).mockResolvedValue([
      {
        operationReference: "CD-0000000002",
        customerName: null,
        accountNumber: "617242043877",
        amount: 500,
        currency: "INR",
        status: "COMPLETED",
        transactionReference: null,
        createdAt: "2026-08-13T10:42:00Z",
      },
    ]);
    renderWithProviders(<CashDepositHistory />);

    await waitFor(() => expect(screen.getByText("CD-0000000002")).toBeInTheDocument());
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("shows an empty state, not a fabricated row, when there are no deposits yet", async () => {
    vi.mocked(operationsService.cashDepositHistory).mockResolvedValue([]);
    renderWithProviders(<CashDepositHistory />);

    await waitFor(() => expect(screen.getByText("No cash deposits yet")).toBeInTheDocument());
  });

  it("shows an error state when the history request fails", async () => {
    vi.mocked(operationsService.cashDepositHistory).mockRejectedValue(new ApiError("Something went wrong", 500));
    renderWithProviders(<CashDepositHistory />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
