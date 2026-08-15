import { describe, expect, it, vi } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/testUtils";
import { ApiError } from "@/utils/apiError";
import Transfer from "./Transfer";
import type { Account } from "@/types/account";
import type { Beneficiary } from "@/types/beneficiary";

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({ customerId: "customer-1" }),
}));

const getAccountsByCustomer = vi.fn();
const resolveRecipient = vi.fn();
const transfer = vi.fn();
vi.mock("@/services/accountService", () => ({
  accountService: {
    getAccountsByCustomer: (...args: unknown[]) => getAccountsByCustomer(...args),
    resolveRecipient: (...args: unknown[]) => resolveRecipient(...args),
    transfer: (...args: unknown[]) => transfer(...args),
  },
}));

const getBeneficiaries = vi.fn();
vi.mock("@/services/beneficiaryService", () => ({
  beneficiaryService: {
    getBeneficiaries: (...args: unknown[]) => getBeneficiaries(...args),
  },
}));

const requestTransferStepUp = vi.fn();
const verifyStepUp = vi.fn();
vi.mock("@/services/stepUpService", () => ({
  stepUpService: {
    requestTransferStepUp: (...args: unknown[]) => requestTransferStepUp(...args),
    verifyStepUp: (...args: unknown[]) => verifyStepUp(...args),
  },
}));

const sourceAccount: Account = {
  id: "acc-source",
  customerId: "customer-1",
  accountNumber: "111111111111",
  ifsc: "BANK0000001",
  accountType: "SAVINGS",
  balance: 10000,
  currency: "INR",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const beneficiary: Beneficiary = {
  id: "ben-1",
  customerId: "customer-1",
  beneficiaryName: "Rahul Sharma",
  accountNumber: "617242043877",
  ifsc: "BANK0000001",
  bankName: "BankSphere",
  nickname: null,
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

async function selectSourceAccount() {
  await userEvent.click(await screen.findByText(/Savings Account/));
  await userEvent.click(screen.getByRole("button", { name: "Continue" }));
}

describe("Transfer page", () => {
  it("requires a source account before continuing", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([sourceAccount]);
    getBeneficiaries.mockResolvedValueOnce([]);

    renderWithProviders(<Transfer />);
    await screen.findByText("From which account?");

    await userEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("Select an account to transfer from.")).toBeInTheDocument();
  });

  it("disables 'Verify recipient' until both the account number and IFSC are validly formatted", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([sourceAccount]);
    getBeneficiaries.mockResolvedValueOnce([]);

    renderWithProviders(<Transfer />);
    await selectSourceAccount();
    await userEvent.click(screen.getByRole("tab", { name: "Account number" }));

    const verifyButton = screen.getByRole("button", { name: "Verify recipient" });
    expect(verifyButton).toBeDisabled();

    await userEvent.type(screen.getByLabelText("Recipient Account Number"), "12345");
    expect(verifyButton).toBeDisabled();

    await userEvent.type(screen.getByLabelText("Recipient Account Number"), "6789012");
    await userEvent.type(screen.getByLabelText("IFSC Code"), "notanifsc");
    expect(verifyButton).toBeDisabled();

    await userEvent.clear(screen.getByLabelText("IFSC Code"));
    await userEvent.type(screen.getByLabelText("IFSC Code"), "bank0000001");
    expect(verifyButton).not.toBeDisabled();

    expect(resolveRecipient).not.toHaveBeenCalled();
  });

  it("resolves a manually-entered account number + IFSC via the real API and shows a recipient preview — never asking for a UUID", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([sourceAccount]);
    getBeneficiaries.mockResolvedValueOnce([]);
    resolveRecipient.mockResolvedValueOnce({ accountNumber: "617242043877", ifsc: "BANK0000001", bankName: "BankSphere" });

    renderWithProviders(<Transfer />);
    await selectSourceAccount();
    await userEvent.click(screen.getByRole("tab", { name: "Account number" }));

    expect(screen.queryByText(/Account ID/)).not.toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("Recipient Account Number"), "617242043877");
    await userEvent.type(screen.getByLabelText("IFSC Code"), "bank0000001");
    await userEvent.click(screen.getByRole("button", { name: "Verify recipient" }));

    await waitFor(() => expect(resolveRecipient).toHaveBeenCalledWith({ accountNumber: "617242043877", ifsc: "BANK0000001" }));

    expect(await screen.findByText(/Account ending 3877/)).toBeInTheDocument();
    expect(screen.getByText("BankSphere")).toBeInTheDocument();
    expect(screen.getByText(/Account ending 3877/)).toBeInTheDocument();
  });

  it("shows a friendly message when the recipient can't be found, without a fabricated preview", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([sourceAccount]);
    getBeneficiaries.mockResolvedValueOnce([]);
    resolveRecipient.mockRejectedValueOnce(new ApiError("No BankSphere account found for account number 999999999999", 404));

    renderWithProviders(<Transfer />);
    await selectSourceAccount();
    await userEvent.click(screen.getByRole("tab", { name: "Account number" }));
    await userEvent.type(screen.getByLabelText("Recipient Account Number"), "999999999999");
    await userEvent.type(screen.getByLabelText("IFSC Code"), "bank0000001");
    await userEvent.click(screen.getByRole("button", { name: "Verify recipient" }));

    expect(await screen.findByText("We couldn't find a BankSphere account with that account number.")).toBeInTheDocument();
    expect(screen.queryByText(/Account ending/)).not.toBeInTheDocument();
  });

  it("selecting a saved beneficiary automatically resolves the recipient — no account id ever required from the user", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([sourceAccount]);
    getBeneficiaries.mockResolvedValueOnce([beneficiary]);
    resolveRecipient.mockResolvedValueOnce({ accountNumber: "617242043877", ifsc: "BANK0000001", bankName: "BankSphere" });

    renderWithProviders(<Transfer />);
    await selectSourceAccount();
    await userEvent.click(screen.getByRole("tab", { name: "Saved beneficiary" }));

    await userEvent.click(await screen.findByText("Rahul Sharma"));

    await waitFor(() => expect(resolveRecipient).toHaveBeenCalledWith({ accountNumber: "617242043877", ifsc: "BANK0000001" }));
    expect(await screen.findByText(/Account ending 3877/)).toBeInTheDocument();
    // The beneficiary's own saved name is shown — real data, not fabricated.
    expect(screen.getAllByText("Rahul Sharma").length).toBeGreaterThan(0);
  });

  it("submits a transfer using the resolved account number + IFSC, never an internal UUID", async () => {
    getAccountsByCustomer.mockResolvedValue([sourceAccount]);
    getBeneficiaries.mockResolvedValueOnce([]);
    resolveRecipient.mockResolvedValueOnce({ accountNumber: "617242043877", ifsc: "BANK0000001", bankName: "BankSphere" });
    transfer.mockResolvedValueOnce({
      transferId: "transfer-abc",
      sourceAccountId: "acc-source",
      destinationAccountNumber: "617242043877",
      destinationIfsc: "BANK0000001",
      amount: 5000,
      currency: "INR",
      status: "COMPLETED",
      createdAt: "2026-01-05T00:00:00Z",
    });

    renderWithProviders(<Transfer />);
    await selectSourceAccount();
    await userEvent.click(screen.getByRole("tab", { name: "Account number" }));
    await userEvent.type(screen.getByLabelText("Recipient Account Number"), "617242043877");
    await userEvent.type(screen.getByLabelText("IFSC Code"), "bank0000001");
    await userEvent.click(screen.getByRole("button", { name: "Verify recipient" }));
    await screen.findByText(/Account ending 3877/);
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));

    await userEvent.type(screen.getByPlaceholderText("0.00"), "5000");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    await screen.findByText("Review transfer");

    await userEvent.click(screen.getByRole("button", { name: "Confirm & Transfer" }));

    await waitFor(() =>
      expect(transfer).toHaveBeenCalledWith({
        sourceAccountId: "acc-source",
        destinationAccountNumber: "617242043877",
        destinationIfsc: "BANK0000001",
        amount: 5000,
        description: undefined,
        // Phase 9D — no step-up needed on this first attempt (below the
        // policy threshold); idempotencyKey is always present, generated
        // client-side once the customer reaches Review — see Transfer.tsx.
        stepUpChallengeId: undefined,
        idempotencyKey: expect.any(String),
      }),
    );

    expect(await screen.findByText("Transfer successful")).toBeInTheDocument();
    expect(screen.getByText(/transfer-abc/)).toBeInTheDocument();
    await waitFor(() => expect(getAccountsByCustomer).toHaveBeenCalledTimes(2));
  });

  it("prompts for step-up verification when the backend requires it, then retries the SAME transfer with the verified challengeId", async () => {
    const highBalanceAccount: Account = { ...sourceAccount, balance: 500000 };
    getAccountsByCustomer.mockResolvedValue([highBalanceAccount]);
    getBeneficiaries.mockResolvedValueOnce([]);
    resolveRecipient.mockResolvedValueOnce({ accountNumber: "617242043877", ifsc: "BANK0000001", bankName: "BankSphere" });
    // First attempt: the backend's own policy (not the frontend) decides
    // step-up is required for this amount — see StepUpPolicy/ADR-009.
    transfer.mockRejectedValueOnce(new ApiError("Step-up authentication is required for this operation", 403));
    transfer.mockResolvedValueOnce({
      transferId: "transfer-xyz",
      sourceAccountId: "acc-source",
      destinationAccountNumber: "617242043877",
      destinationIfsc: "BANK0000001",
      amount: 100000,
      currency: "INR",
      status: "COMPLETED",
      createdAt: "2026-01-05T00:00:00Z",
    });
    requestTransferStepUp.mockResolvedValueOnce({ challengeId: "challenge-1", expiresAt: "2026-01-05T00:05:00Z" });
    verifyStepUp.mockResolvedValueOnce({ verified: true, challengeId: "challenge-1", expiresAt: "2026-01-05T00:05:00Z" });

    renderWithProviders(<Transfer />);
    await selectSourceAccount();
    await userEvent.click(screen.getByRole("tab", { name: "Account number" }));
    await userEvent.type(screen.getByLabelText("Recipient Account Number"), "617242043877");
    await userEvent.type(screen.getByLabelText("IFSC Code"), "bank0000001");
    await userEvent.click(screen.getByRole("button", { name: "Verify recipient" }));
    await screen.findByText(/Account ending 3877/);
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));

    await userEvent.type(screen.getByPlaceholderText("0.00"), "100000");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    await screen.findByText("Review transfer");

    await userEvent.click(screen.getByRole("button", { name: "Confirm & Transfer" }));

    // The step-up modal appears — never a generic error — and requests a
    // fresh challenge bound to this exact operation.
    expect(await screen.findByText("Verify it's you")).toBeInTheDocument();
    await waitFor(() =>
      expect(requestTransferStepUp).toHaveBeenCalledWith({
        sourceAccountId: "acc-source",
        destinationAccountNumber: "617242043877",
        destinationIfsc: "BANK0000001",
        amount: 100000,
        currency: "INR",
      }),
    );

    await userEvent.type(await screen.findByLabelText("6-digit code"), "123456");
    await userEvent.click(screen.getByRole("button", { name: "Verify" }));

    expect(verifyStepUp).toHaveBeenCalledWith("challenge-1", "123456");
    await waitFor(() => expect(transfer).toHaveBeenCalledTimes(2));

    const [firstCallArgs] = transfer.mock.calls[0] as [{ idempotencyKey: string }];
    const [secondCallArgs] = transfer.mock.calls[1] as [{ stepUpChallengeId: string; idempotencyKey: string }];
    // Same idempotency key on the retry — this is the SAME logical
    // operation being authorized, not a new one.
    expect(secondCallArgs.idempotencyKey).toBe(firstCallArgs.idempotencyKey);
    expect(secondCallArgs.stepUpChallengeId).toBe("challenge-1");

    expect(await screen.findByText("Transfer successful")).toBeInTheDocument();
  });

  it("prevents a double submission from firing two transfer requests", async () => {
    getAccountsByCustomer.mockResolvedValue([sourceAccount]);
    getBeneficiaries.mockResolvedValueOnce([]);
    resolveRecipient.mockResolvedValueOnce({ accountNumber: "617242043877", ifsc: "BANK0000001", bankName: "BankSphere" });
    let resolveTransfer: (value: unknown) => void = () => {};
    transfer.mockReturnValueOnce(new Promise((resolve) => (resolveTransfer = resolve)));

    renderWithProviders(<Transfer />);
    await selectSourceAccount();
    await userEvent.click(screen.getByRole("tab", { name: "Account number" }));
    await userEvent.type(screen.getByLabelText("Recipient Account Number"), "617242043877");
    await userEvent.type(screen.getByLabelText("IFSC Code"), "bank0000001");
    await userEvent.click(screen.getByRole("button", { name: "Verify recipient" }));
    await screen.findByText(/Account ending 3877/);
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    await userEvent.type(screen.getByPlaceholderText("0.00"), "5000");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    await screen.findByText("Review transfer");

    const confirmButton = screen.getByRole("button", { name: "Confirm & Transfer" });
    fireEvent.click(confirmButton);
    fireEvent.click(confirmButton);
    fireEvent.click(confirmButton);

    await waitFor(() => expect(transfer).toHaveBeenCalledTimes(1));

    resolveTransfer({
      transferId: "transfer-xyz",
      sourceAccountId: "acc-source",
      destinationAccountNumber: "617242043877",
      destinationIfsc: "BANK0000001",
      amount: 5000,
      currency: "INR",
      status: "COMPLETED",
      createdAt: "2026-01-05T00:00:00Z",
    });
    await screen.findByText("Transfer successful");
    expect(transfer).toHaveBeenCalledTimes(1);
  });

  it("shows the backend's rejection reason and does not show a success screen when the transfer fails", async () => {
    getAccountsByCustomer.mockResolvedValue([sourceAccount]);
    getBeneficiaries.mockResolvedValueOnce([]);
    resolveRecipient.mockResolvedValueOnce({ accountNumber: "617242043877", ifsc: "BANK0000001", bankName: "BankSphere" });
    transfer.mockRejectedValueOnce(new ApiError("Insufficient balance", 422));

    renderWithProviders(<Transfer />);
    await selectSourceAccount();
    await userEvent.click(screen.getByRole("tab", { name: "Account number" }));
    await userEvent.type(screen.getByLabelText("Recipient Account Number"), "617242043877");
    await userEvent.type(screen.getByLabelText("IFSC Code"), "bank0000001");
    await userEvent.click(screen.getByRole("button", { name: "Verify recipient" }));
    await screen.findByText(/Account ending 3877/);
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    await userEvent.type(screen.getByPlaceholderText("0.00"), "5000");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    await screen.findByText("Review transfer");

    await userEvent.click(screen.getByRole("button", { name: "Confirm & Transfer" }));

    expect(await screen.findByText(/Insufficient available balance/)).toBeInTheDocument();
    expect(screen.queryByText("Transfer successful")).not.toBeInTheDocument();
  });
});
