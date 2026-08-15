import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import { vi } from "vitest";

// Vitest's `globals` mode is deliberately off (test files import
// describe/it/expect/vi explicitly), so React Testing Library's automatic
// per-test DOM cleanup — which relies on detecting a global `afterEach` —
// doesn't register itself. Without this, each test's rendered output
// accumulates in `document.body`, and later tests see duplicate elements
// from earlier tests in the same file.
afterEach(() => {
  cleanup();
});

// Module-level vi.fn() mocks (the convention every test file here uses for
// mocked services) are created once per file and shared across all of that
// file's tests — without a reset, call counts and queued
// mockResolvedValueOnce return values leak from one test into the next.
afterEach(() => {
  vi.resetAllMocks();
});
