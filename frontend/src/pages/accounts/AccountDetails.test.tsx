import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ToastProvider } from "@/components/common/Toast";
import { ApiError } from "@/utils/apiError";
import AccountDetails from "./AccountDetails";
import type { Account } from "@/types/account";

const getAccount = vi.fn();
const deposit = vi.fn();
const withdraw = vi.fn();
vi.mock("@/services/accountService", () => ({
  accountService: {
    getAccount: (...args: unknown[]) => getAccount(...args),
    deposit: (...args: unknown[]) => deposit(...args),
    withdraw: (...args: unknown[]) => withdraw(...args),
  },
}));

const getTransactionsByAccount = vi.fn();
vi.mock("@/services/transactionService", () => ({
  transactionService: {
    getTransactionsByAccount: (...args: unknown[]) => getTransactionsByAccount(...args),
  },
}));

const account: Account = {
  id: "acc-1",
  customerId: "customer-1",
  accountNumber: "123456789012",
  ifsc: "BANK0000001",
  accountType: "SAVINGS",
  balance: 1000,
  currency: "INR",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/accounts/acc-1"]}>
      <ToastProvider>
        <Routes>
          <Route path="/accounts/:id" element={<AccountDetails />} />
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe("AccountDetails page — deposit/withdraw", () => {
  it("displays the real, server-assigned account number and IFSC, never generated in the browser", async () => {
    getAccount.mockResolvedValue(account);
    getTransactionsByAccount.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });

    renderPage();

    expect(await screen.findByText("123456789012")).toBeInTheDocument();
    expect(screen.getByText("BANK0000001")).toBeInTheDocument();
  });

  it("shows a friendly, non-generic message for a 403 (account not owned by caller)", async () => {
    getAccount.mockResolvedValue(account);
    getTransactionsByAccount.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });
    deposit.mockRejectedValueOnce(new ApiError("Not authorized to access this resource", 403));

    renderPage();
    await screen.findByText("Deposit funds");
    await userEvent.type(screen.getByPlaceholderText("0.00"), "500");
    await userEvent.click(screen.getByRole("button", { name: "Deposit funds" }));

    expect(await screen.findByText("You are not authorized to perform this operation.")).toBeInTheDocument();
  });

  it("shows a friendly, non-generic message for a 500, not a raw stack trace", async () => {
    getAccount.mockResolvedValue(account);
    getTransactionsByAccount.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });
    deposit.mockRejectedValueOnce(new ApiError("Internal Server Error", 500));

    renderPage();
    await screen.findByText("Deposit funds");
    await userEvent.type(screen.getByPlaceholderText("0.00"), "500");
    await userEvent.click(screen.getByRole("button", { name: "Deposit funds" }));

    expect(await screen.findByText("Something went wrong on our end. Please try again shortly.")).toBeInTheDocument();
  });

  it("rejects a zero amount before ever calling the backend", async () => {
    getAccount.mockResolvedValue(account);
    getTransactionsByAccount.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });

    renderPage();
    await screen.findByText("Deposit funds");

    await userEvent.click(screen.getByRole("button", { name: "Deposit funds" }));

    expect(await screen.findByText("Enter an amount greater than zero.")).toBeInTheDocument();
    expect(deposit).not.toHaveBeenCalled();
  });

  it("submits a valid deposit, then refreshes the account and transaction history", async () => {
    getAccount.mockResolvedValue(account);
    getTransactionsByAccount.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });
    deposit.mockResolvedValueOnce({ ...account, balance: 1500 });

    renderPage();
    await screen.findByText("Deposit funds");

    const amountInput = screen.getByPlaceholderText("0.00");
    await userEvent.type(amountInput, "500");
    await userEvent.click(screen.getByRole("button", { name: "Deposit funds" }));

    await waitFor(() =>
      expect(deposit).toHaveBeenCalledWith("acc-1", { amount: 500, description: undefined }),
    );
    // reload() + reloadTransactions() re-invoke the same fetchers.
    await waitFor(() => expect(getAccount).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(getTransactionsByAccount).toHaveBeenCalledTimes(2));
  });

  it("rejects a withdrawal amount that isn't a valid positive number", async () => {
    getAccount.mockResolvedValue(account);
    getTransactionsByAccount.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });

    renderPage();
    await screen.findByText("Deposit funds");
    await userEvent.click(screen.getByRole("tab", { name: "Withdraw" }));

    await userEvent.click(screen.getByRole("button", { name: "Withdraw funds" }));

    expect(await screen.findByText("Enter an amount greater than zero.")).toBeInTheDocument();
    expect(withdraw).not.toHaveBeenCalled();
  });

  it("shows the backend's error message when a withdrawal is rejected (e.g. insufficient balance)", async () => {
    getAccount.mockResolvedValue(account);
    getTransactionsByAccount.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });
    withdraw.mockRejectedValueOnce(new Error("Account has insufficient balance: available=1000.00, requested=5000.00"));

    renderPage();
    await screen.findByText("Deposit funds");
    await userEvent.click(screen.getByRole("tab", { name: "Withdraw" }));

    const amountInput = screen.getByPlaceholderText("0.00");
    await userEvent.type(amountInput, "5000");
    await userEvent.click(screen.getByRole("button", { name: "Withdraw funds" }));

    expect(await screen.findByText(/insufficient balance/)).toBeInTheDocument();
  });
});
