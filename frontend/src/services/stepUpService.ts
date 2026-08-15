import { customerApiClient } from "./apiClient";
import type { StepUpRequestResponse, StepUpTransferContext, StepUpVerifyResponse } from "@/types/auth";

/**
 * Phase 9D — step-up authentication for high-risk operations (currently:
 * transfers at or above account-service's configured threshold). This is
 * a customer-service concern, not account-service — see ADR-009's
 * customer-vs-account-service boundary — so it goes through
 * `customerApiClient`, not `accountApiClient`, even though the operation
 * it protects (a transfer) lives in account-service.
 */
export const stepUpService = {
  async requestTransferStepUp(transferContext: StepUpTransferContext): Promise<StepUpRequestResponse> {
    const { data } = await customerApiClient.post<StepUpRequestResponse>("/api/v1/auth/step-up/request", {
      purpose: "STEP_UP_TRANSFER",
      transferContext,
    });
    return data;
  },

  async verifyStepUp(challengeId: string, otp: string): Promise<StepUpVerifyResponse> {
    const { data } = await customerApiClient.post<StepUpVerifyResponse>("/api/v1/auth/step-up/verify", {
      challengeId,
      otp,
    });
    return data;
  },
};
