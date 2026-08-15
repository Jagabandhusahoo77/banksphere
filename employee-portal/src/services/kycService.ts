import { kycApiClient } from "./apiClient";
import type { KycApplicationDetail, KycDocument, KycQueueItem, KycStatus } from "@/types/kyc";

export const kycService = {
  async getQueue(status?: KycStatus): Promise<KycQueueItem[]> {
    const { data } = await kycApiClient.get<KycQueueItem[]>("/api/v1/kyc/employee/queue", {
      params: status ? { status } : undefined,
    });
    return data;
  },

  async getApplication(id: string): Promise<KycApplicationDetail> {
    const { data } = await kycApiClient.get<KycApplicationDetail>(`/api/v1/kyc/employee/applications/${id}`);
    return data;
  },

  async startReview(id: string): Promise<KycApplicationDetail> {
    const { data } = await kycApiClient.post<KycApplicationDetail>(`/api/v1/kyc/employee/applications/${id}/start-review`);
    return data;
  },

  async verifyDocument(documentId: string): Promise<KycDocument> {
    const { data } = await kycApiClient.post<KycDocument>(`/api/v1/kyc/employee/documents/${documentId}/verify`);
    return data;
  },

  async rejectDocument(documentId: string, reason: string): Promise<KycDocument> {
    const { data } = await kycApiClient.post<KycDocument>(`/api/v1/kyc/employee/documents/${documentId}/reject`, { reason });
    return data;
  },

  async requestInformation(id: string, reason: string): Promise<KycApplicationDetail> {
    const { data } = await kycApiClient.post<KycApplicationDetail>(
      `/api/v1/kyc/employee/applications/${id}/request-information`,
      { reason },
    );
    return data;
  },

  async approve(id: string): Promise<KycApplicationDetail> {
    const { data } = await kycApiClient.post<KycApplicationDetail>(`/api/v1/kyc/employee/applications/${id}/approve`);
    return data;
  },

  async reject(id: string, reason: string): Promise<KycApplicationDetail> {
    const { data } = await kycApiClient.post<KycApplicationDetail>(`/api/v1/kyc/employee/applications/${id}/reject`, { reason });
    return data;
  },
};
