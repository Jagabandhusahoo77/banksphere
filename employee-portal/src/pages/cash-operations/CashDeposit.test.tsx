import { describe, it, expect, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import CashDeposit from "./CashDeposit";
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

const mockUseAuth = vi.fn();
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

const sampleEmployee = {
  id: "emp-1",
  employeeNumber: "EMP000010",
  username: "jane.teller",
  firstName: "Jane",
  lastName: "Teller",
  email: "jane.teller@banksphere.example",
  status: "ACTIVE" as const,
  roles: ["TELLER"],
  permissions: ["CASH_DEPOSIT"],
  branch: { id: "branch-1", branchCode: "HQ001", branchName: "BankSphere Head Office", ifsc: "BANK0000001" },
};

const searchResponse = {
  customerId: "cust-1",
  customerName: "John Smith",
  accounts: [
    { id: "acc-1", accountNumber: "617242043877", accountType: "SAVINGS", balance: 20000, currency: "INR", status: "ACTIVE" },
  ],
};

describe("CashDeposit", () => {
  it("walks through search -> select account -> amount -> review -> confirm, showing the real operation/transaction references", async () => {
    mockUseAuth.mockReturnValue({ employee: sampleEmployee });
    vi.mocked(operationsService.customerSearchByAccountNumber).mockResolvedValue(searchResponse);
    vi.mocked(operationsService.cashDeposit).mockResolvedValue({
      operationReference: "CD-0000000001",
      accountId: "acc-1",
      accountNumber: "617242043877",
      newBalance: 30000,
      currency: "INR",
      transactionReference: "TXN-ABC123",
      status: "COMPLETED",
      performedBy: "EMP000010",
      branchCode: "HQ001",
    });
    const user = userEvent.setup();
    renderWithProviders(<CashDeposit />);

    await user.type(screen.getByLabelText("Recipient Account Number"), "617242043877");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByText(/John Smith/)).toBeInTheDocument();
    await user.click(screen.getByText(/Savings Account/));

    await user.type(screen.getByLabelText("Amount (INR)"), "10000");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("Review Cash Deposit")).toBeInTheDocument();
    expect(screen.getByText("BankSphere Head Office (HQ001)")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Confirm Cash Deposit" }));

    expect(await screen.findByText("Cash deposit completed")).toBeInTheDocument();
    expect(screen.getByText("CD-0000000001")).toBeInTheDocument();
    expect(screen.getByText("TXN-ABC123")).toBeInTheDocument();

    expect(operationsService.cashDeposit).toHaveBeenCalledWith({ accountId: "acc-1", amount: 10000, description: undefined });
  });

  it("shows a friendly 404 message and does not advance when no customer is found", async () => {
    mockUseAuth.mockReturnValue({ employee: sampleEmployee });
    vi.mocked(operationsService.customerSearchByAccountNumber).mockRejectedValue(new ApiError("not found", 404));
    const user = userEvent.setup();
    renderWithProviders(<CashDeposit />);

    await user.type(screen.getByLabelText("Recipient Account Number"), "999999999999");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("No customer found with that account number.");
    expect(screen.queryByText("Select Account")).not.toBeInTheDocument();
  });

  it("shows a friendly error and stays on the review step when the backend rejects the deposit (e.g. branch scope)", async () => {
    mockUseAuth.mockReturnValue({ employee: sampleEmployee });
    vi.mocked(operationsService.customerSearchByAccountNumber).mockResolvedValue(searchResponse);
    vi.mocked(operationsService.cashDeposit).mockRejectedValue(new ApiError("Outside the caller's own branch", 403));
    const user = userEvent.setup();
    renderWithProviders(<CashDeposit />);

    await user.type(screen.getByLabelText("Recipient Account Number"), "617242043877");
    await user.click(screen.getByRole("button", { name: "Search" }));
    await user.click(await screen.findByText(/Savings Account/));
    await user.type(screen.getByLabelText("Amount (INR)"), "10000");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.click(screen.getByRole("button", { name: "Confirm Cash Deposit" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("not authorized to credit this account");
    expect(screen.getByText("Review Cash Deposit")).toBeInTheDocument();
  });

  it("prevents double submission while a deposit is in flight", async () => {
    mockUseAuth.mockReturnValue({ employee: sampleEmployee });
    vi.mocked(operationsService.customerSearchByAccountNumber).mockResolvedValue(searchResponse);
    let resolveDeposit: (value: Awaited<ReturnType<typeof operationsService.cashDeposit>>) => void = () => {};
    vi.mocked(operationsService.cashDeposit).mockReturnValue(
      new Promise((resolve) => {
        resolveDeposit = resolve;
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<CashDeposit />);

    await user.type(screen.getByLabelText("Recipient Account Number"), "617242043877");
    await user.click(screen.getByRole("button", { name: "Search" }));
    await user.click(await screen.findByText(/Savings Account/));
    await user.type(screen.getByLabelText("Amount (INR)"), "10000");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    const confirmButton = screen.getByRole("button", { name: "Confirm Cash Deposit" });
    await user.click(confirmButton);
    await user.click(confirmButton);
    await user.click(confirmButton);

    expect(operationsService.cashDeposit).toHaveBeenCalledTimes(1);

    resolveDeposit({
      operationReference: "CD-1", accountId: "acc-1", accountNumber: "617242043877", newBalance: 30000,
      currency: "INR", transactionReference: "TXN-1", status: "COMPLETED", performedBy: "EMP000010", branchCode: "HQ001",
    });
    await waitFor(() => expect(screen.getByText("Cash deposit completed")).toBeInTheDocument());
  });

  it("disables Search until a full 12-digit account number is entered", async () => {
    mockUseAuth.mockReturnValue({ employee: sampleEmployee });
    const user = userEvent.setup();
    renderWithProviders(<CashDeposit />);

    const searchButton = screen.getByRole("button", { name: "Search" });
    expect(searchButton).toBeDisabled();

    await user.type(screen.getByLabelText("Recipient Account Number"), "12345");
    expect(searchButton).toBeDisabled();

    await user.type(screen.getByLabelText("Recipient Account Number"), "67890123");
    expect(searchButton).toBeEnabled();
  });
});
