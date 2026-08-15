import { describe, expect, it } from "vitest";
import { ApiError, getFriendlyErrorMessage } from "./apiError";

describe("getFriendlyErrorMessage", () => {
  it("maps a 401 to a session-expired message", () => {
    expect(getFriendlyErrorMessage(new ApiError("Unauthorized", 401))).toBe(
      "Your session has expired. Please sign in again.",
    );
  });

  it("maps a 403 to an authorization message", () => {
    expect(getFriendlyErrorMessage(new ApiError("Forbidden", 403))).toBe(
      "You are not authorized to perform this operation.",
    );
  });

  it("maps a 404 to a not-found message", () => {
    expect(getFriendlyErrorMessage(new ApiError("Not Found", 404))).toBe(
      "We couldn't find what you were looking for.",
    );
  });

  it("maps a 409 to a conflict/refresh message", () => {
    expect(getFriendlyErrorMessage(new ApiError("Conflict", 409))).toBe(
      "This was updated by another operation. Please refresh and try again.",
    );
  });

  it("uses the backend's own message for a 422 by default", () => {
    expect(getFriendlyErrorMessage(new ApiError("Account has insufficient balance", 422))).toBe(
      "Account has insufficient balance",
    );
  });

  it("lets a caller override the message for a specific status", () => {
    expect(
      getFriendlyErrorMessage(new ApiError("Account has insufficient balance", 422), {
        422: "Insufficient available balance for this transfer.",
      }),
    ).toBe("Insufficient available balance for this transfer.");
  });

  it("maps a 500 to a generic server-error message", () => {
    expect(getFriendlyErrorMessage(new ApiError("boom", 500))).toBe(
      "Something went wrong on our end. Please try again shortly.",
    );
  });

  it("treats a status-less ApiError as a network failure", () => {
    expect(getFriendlyErrorMessage(new ApiError("Network Error"))).toBe(
      "Couldn't reach the server. Check your connection and try again.",
    );
  });

  it("falls back to a plain Error's message", () => {
    expect(getFriendlyErrorMessage(new Error("Something specific"))).toBe("Something specific");
  });

  it("falls back to a generic message for a non-Error value", () => {
    expect(getFriendlyErrorMessage("nope")).toBe("Something went wrong. Please try again.");
  });
});
