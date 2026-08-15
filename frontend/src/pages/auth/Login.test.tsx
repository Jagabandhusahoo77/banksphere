import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/testUtils";
import { ApiError } from "@/utils/apiError";
import Login from "./Login";

const login = vi.fn();
const requestOtp = vi.fn();
const verifyOtp = vi.fn();

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    isAuthenticated: false,
    login: (...args: unknown[]) => login(...args),
    requestOtp: (...args: unknown[]) => requestOtp(...args),
    verifyOtp: (...args: unknown[]) => verifyOtp(...args),
  }),
}));

const navigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigate,
    useLocation: () => ({ state: null }),
  };
});

describe("Login page", () => {
  it("signs in with email + password by default, unchanged from before Phase 9D", async () => {
    login.mockResolvedValueOnce(undefined);
    renderWithProviders(<Login />);

    await userEvent.type(screen.getByLabelText("Email address"), "jane.doe@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "Password123");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(login).toHaveBeenCalledWith("jane.doe@example.com", "Password123");
  });

  it("shows a generic error for invalid password credentials, never revealing whether the email exists", async () => {
    login.mockRejectedValueOnce(new ApiError("Invalid email or password", 401));
    renderWithProviders(<Login />);

    await userEvent.type(screen.getByLabelText("Email address"), "jane.doe@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "WrongPassword1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Incorrect email or password. Please try again.")).toBeInTheDocument();
  });

  it("switching to one-time code mode requests an OTP, then verifying it signs the customer in", async () => {
    requestOtp.mockResolvedValueOnce({ challengeId: "challenge-1" });
    verifyOtp.mockResolvedValueOnce(undefined);
    renderWithProviders(<Login />);

    await userEvent.click(screen.getByRole("tab", { name: "One-time code" }));
    await userEvent.type(screen.getByLabelText("Email or phone number"), "jane.doe@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send one-time code" }));

    expect(requestOtp).toHaveBeenCalledWith("jane.doe@example.com");
    expect(await screen.findByLabelText("6-digit code")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("6-digit code"), "123456");
    await userEvent.click(screen.getByRole("button", { name: "Verify & sign in" }));

    expect(verifyOtp).toHaveBeenCalledWith("challenge-1", "123456");
  });

  it("shows a generic error for an invalid OTP, matching the backend's own generic message", async () => {
    requestOtp.mockResolvedValueOnce({ challengeId: "challenge-1" });
    verifyOtp.mockRejectedValueOnce(new ApiError("Invalid or expired OTP", 400));
    renderWithProviders(<Login />);

    await userEvent.click(screen.getByRole("tab", { name: "One-time code" }));
    await userEvent.type(screen.getByLabelText("Email or phone number"), "jane.doe@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send one-time code" }));
    await screen.findByLabelText("6-digit code");

    await userEvent.type(screen.getByLabelText("6-digit code"), "000000");
    await userEvent.click(screen.getByRole("button", { name: "Verify & sign in" }));

    expect(await screen.findByText("That code is incorrect or has expired. Please try again.")).toBeInTheDocument();
  });

  it("never reveals via the OTP-request response whether the identifier is registered", async () => {
    // Same generic response is shown whether or not requestOtp actually
    // resolved to a real customer's challenge — the frontend has no way
    // to distinguish these, by design (see ADR-009's enumeration section).
    requestOtp.mockResolvedValueOnce({ challengeId: "challenge-2" });
    renderWithProviders(<Login />);

    await userEvent.click(screen.getByRole("tab", { name: "One-time code" }));
    await userEvent.type(screen.getByLabelText("Email or phone number"), "unknown@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send one-time code" }));

    expect(
      await screen.findByText("If that email or phone is registered with BankSphere, a 6-digit code has been sent."),
    ).toBeInTheDocument();
  });
});
