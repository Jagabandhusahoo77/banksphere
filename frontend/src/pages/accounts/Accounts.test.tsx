import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/testUtils";
import Accounts from "./Accounts";
import type { Account } from "@/types/account";

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({ customerId: "customer-1" }),
}));

const getAccountsByCustomer = vi.fn();
const createAccount = vi.fn();
vi.mock("@/services/accountService", () => ({
  accountService: {
    getAccountsByCustomer: (...args: unknown[]) => getAccountsByCustomer(...args),
    createAccount: (...args: unknown[]) => createAccount(...args),
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

describe("Accounts page", () => {
  it("loads and displays real accounts from the account service", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([account]);

    renderWithProviders(<Accounts />);

    await waitFor(() => expect(getAccountsByCustomer).toHaveBeenCalledWith("customer-1"));
    expect(await screen.findByText(/•••• 9012/)).toBeInTheDocument();
  });

  it("shows an empty state when the customer genuinely has no accounts", async () => {
    getAccountsByCustomer.mockResolvedValueOnce([]);

    renderWithProviders(<Accounts />);

    expect(await screen.findByText("No accounts found")).toBeInTheDocument();
  });

  it("opens a new account through the real API, sending only accountType/currency/initialDeposit — never accountNumber, ifsc, or customerId", async () => {
    getAccountsByCustomer.mockResolvedValue([]);
    createAccount.mockResolvedValueOnce({
      ...account,
      id: "acc-2",
      accountNumber: "999888777666",
      ifsc: "BANK0000001",
    });

    renderWithProviders(<Accounts />);
    await screen.findByText("No accounts found");

    await userEvent.click(screen.getAllByRole("button", { name: "Open new account" })[0]);
    await userEvent.click(screen.getByRole("button", { name: "Open account" }));

    await waitFor(() =>
      expect(createAccount).toHaveBeenCalledWith({
        accountType: "SAVINGS",
        currency: "INR",
        initialDeposit: undefined,
      }),
    );

    // The generated account number/IFSC are displayed back — never
    // computed or invented in the browser, only shown from the real
    // backend response.
    expect(await screen.findByText("999888777666")).toBeInTheDocument();
    expect(screen.getByText("BANK0000001")).toBeInTheDocument();
  });

  it("shows the backend's error message if account creation fails, without a fabricated success", async () => {
    getAccountsByCustomer.mockResolvedValue([]);
    createAccount.mockRejectedValueOnce(new Error("Something went wrong"));

    renderWithProviders(<Accounts />);
    await screen.findByText("No accounts found");

    await userEvent.click(screen.getAllByRole("button", { name: "Open new account" })[0]);
    await userEvent.click(screen.getByRole("button", { name: "Open account" }));

    expect(await screen.findByText("Something went wrong")).toBeInTheDocument();
    expect(screen.queryByText("Account opened")).not.toBeInTheDocument();
  });
});
