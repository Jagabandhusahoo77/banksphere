import { beneficiaryApiClient } from "./apiClient";
import type { Beneficiary, CreateBeneficiaryRequest, UpdateBeneficiaryRequest } from "@/types/beneficiary";

export const beneficiaryService = {
  async getBeneficiaries(): Promise<Beneficiary[]> {
    const { data } = await beneficiaryApiClient.get<Beneficiary[]>("/api/v1/beneficiaries");
    return data;
  },

  async getBeneficiary(id: string): Promise<Beneficiary> {
    const { data } = await beneficiaryApiClient.get<Beneficiary>(`/api/v1/beneficiaries/${id}`);
    return data;
  },

  async createBeneficiary(request: CreateBeneficiaryRequest): Promise<Beneficiary> {
    const { data } = await beneficiaryApiClient.post<Beneficiary>("/api/v1/beneficiaries", request);
    return data;
  },

  async updateBeneficiary(id: string, request: UpdateBeneficiaryRequest): Promise<Beneficiary> {
    const { data } = await beneficiaryApiClient.put<Beneficiary>(`/api/v1/beneficiaries/${id}`, request);
    return data;
  },

  async deactivateBeneficiary(id: string): Promise<void> {
    await beneficiaryApiClient.delete(`/api/v1/beneficiaries/${id}`);
  },
};
