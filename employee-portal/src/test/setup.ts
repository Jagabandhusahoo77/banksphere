import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import { vi } from "vitest";

// Same two fixes the customer portal's setup.ts documents — see that file
// for the full rationale (Vitest `globals` mode is off, so RTL's automatic
// cleanup never registers itself; module-level vi.fn() mocks leak call
// state across tests in the same file without an explicit reset).
afterEach(() => {
  cleanup();
});

afterEach(() => {
  vi.resetAllMocks();
});
