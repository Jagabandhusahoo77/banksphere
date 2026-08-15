import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "@/test/testUtils";
import Transactions from "./Transactions";
import type { Account } from "@/types/account";
import type { PageResponse, Transaction } from "@/types/transaction";

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({ customerId: "customer-1" }),
}));

const getAccountsByCustomer = vi.fn();
vi.mock("@/services/accountService", () => ({
  accountService: {
    getAccountsByCustomer: (...args: unknown[]) => getAccountsByCustomer(...args),
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
  balance: 10000,
  currency: "INR",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function pageOf(content: Transaction[]): PageResponse<Transaction> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length > 0 ? 1 : 0, last: true };
}

describe("Transactions page", () => {
  it("shows the real 'No transactions yet' empty state only once the API genuinely returns zero rows", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([account]);
    getTransactionsByAccount.mockResolvedValueOnce(pageOf([]));

    renderWithProviders(<Transactions />);

    await waitFor(() => expect(getTransactionsByAccount).toHaveBeenCalledWith("acc-1", 0, 10));
    expect(await screen.findByText("No transactions yet")).toBeInTheDocument();
  });

  it("renders real transaction rows once the API returns data", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([account]);
    getTransactionsByAccount.mockResolvedValueOnce(
      pageOf([
        {
          id: "txn-1",
          transactionReference: "TXN-ABC123",
          accountId: "acc-1",
          transactionType: "DEPOSIT",
          amount: 5000,
          currency: "INR",
          status: "COMPLETED",
          description: "Initial deposit",
          createdAt: "2026-01-02T00:00:00Z",
        },
      ]),
    );

    renderWithProviders(<Transactions />);

    expect(await screen.findByText("TXN-ABC123")).toBeInTheDocument();
    expect(screen.getByText("Initial deposit")).toBeInTheDocument();
    expect(screen.queryByText("No transactions yet")).not.toBeInTheDocument();
  });

  it("shows an error state (not the empty state) when the transaction fetch fails", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([account]);
    getTransactionsByAccount.mockRejectedValueOnce(new Error("Service unavailable"));

    renderWithProviders(<Transactions />);

    expect(await screen.findByText("Service unavailable")).toBeInTheDocument();
    expect(screen.queryByText("No transactions yet")).not.toBeInTheDocument();
  });
});
