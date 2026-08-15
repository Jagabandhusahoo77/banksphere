import { kycApiClient } from "./apiClient";
import type { CreateKycApplicationRequest, DocumentType, KycApplication, KycDocument } from "@/types/kyc";

export const kycService = {
  /** 404 (surfaced as an ApiError) means the caller has never started KYC — not an error the UI should show as a failure. */
  async getMyApplication(): Promise<KycApplication> {
    const { data } = await kycApiClient.get<KycApplication>("/api/v1/kyc/applications/me");
    return data;
  },

  async createApplication(request: CreateKycApplicationRequest): Promise<KycApplication> {
    const { data } = await kycApiClient.post<KycApplication>("/api/v1/kyc/applications", request);
    return data;
  },

  async uploadDocument(applicationId: string, documentType: DocumentType, file: File): Promise<KycDocument> {
    const formData = new FormData();
    formData.append("file", file);
    const { data } = await kycApiClient.post<KycDocument>(
      `/api/v1/kyc/applications/${applicationId}/documents`,
      formData,
      { params: { documentType }, headers: { "Content-Type": "multipart/form-data" } },
    );
    return data;
  },

  async submit(applicationId: string): Promise<KycApplication> {
    const { data } = await kycApiClient.post<KycApplication>(`/api/v1/kyc/applications/${applicationId}/submit`);
    return data;
  },

  async resubmit(applicationId: string): Promise<KycApplication> {
    const { data } = await kycApiClient.post<KycApplication>(`/api/v1/kyc/applications/${applicationId}/resubmit`);
    return data;
  },
};
