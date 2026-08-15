export type BeneficiaryStatus = "ACTIVE" | "INACTIVE";

export interface Beneficiary {
  id: string;
  customerId: string;
  beneficiaryName: string;
  accountNumber: string;
  ifsc: string;
  bankName: string;
  nickname: string | null;
  status: BeneficiaryStatus;
  createdAt: string;
  updatedAt: string;
}

/** No `customerId` field — the owner is always the authenticated caller, derived from the JWT server-side. */
export interface CreateBeneficiaryRequest {
  beneficiaryName: string;
  accountNumber: string;
  ifsc: string;
  bankName: string;
  nickname?: string;
}

/**
 * Deliberately excludes `accountNumber`/`ifsc` — the backend rejects
 * changing them (see beneficiary-service's `UpdateBeneficiaryRequest`
 * javadoc: identifying fields aren't editable, only display fields are).
 */
export interface UpdateBeneficiaryRequest {
  beneficiaryName: string;
  bankName: string;
  nickname?: string;
}
