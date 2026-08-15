export type KycStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "UNDER_REVIEW"
  | "ADDITIONAL_INFORMATION_REQUIRED"
  | "RESUBMITTED"
  | "APPROVED"
  | "REJECTED";

export type DocumentType = "PAN" | "IDENTITY_PROOF" | "ADDRESS_PROOF" | "BANK_STATEMENT";
export type DocumentStatus = "PENDING" | "VERIFIED" | "REJECTED";

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  PAN: "PAN Card",
  IDENTITY_PROOF: "Identity Proof",
  ADDRESS_PROOF: "Address Proof",
  BANK_STATEMENT: "Bank Statement",
};

export interface KycQueueItem {
  applicationId: string;
  customerId: string;
  submittedAt: string | null;
  status: KycStatus;
  documentsSubmitted: number;
  documentsRequired: number;
  currentReviewerId: string | null;
}

export interface KycDocument {
  id: string;
  documentType: DocumentType;
  documentStatus: DocumentStatus;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  submittedAt: string;
  verifiedAt: string | null;
  rejectionReason: string | null;
}

export interface KycStatusHistoryEntry {
  fromStatus: KycStatus | null;
  toStatus: KycStatus;
  changedByEmployeeId: string | null;
  reason: string | null;
  changedAt: string;
}

/** The employee-facing review-screen view — see kyc-service's KycApplicationDetailResponse. */
export interface KycApplicationDetail {
  id: string;
  customerId: string;
  status: KycStatus;
  panNumber: string;
  occupation: string;
  annualIncomeRange: string;
  currentReviewerId: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  reviewedBy: string | null;
  reviewReason: string | null;
  missingDocumentTypes: DocumentType[];
  documents: KycDocument[];
  statusHistory: KycStatusHistoryEntry[];
  version: number;
  createdAt: string;
  updatedAt: string;
}
