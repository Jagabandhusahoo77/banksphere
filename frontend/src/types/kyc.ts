export type KycStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "UNDER_REVIEW"
  | "ADDITIONAL_INFORMATION_REQUIRED"
  | "RESUBMITTED"
  | "APPROVED"
  | "REJECTED";

/** Do not claim this represents every legally accepted Indian banking document — see kyc-service's DocumentType javadoc. */
export type DocumentType = "PAN" | "IDENTITY_PROOF" | "ADDRESS_PROOF" | "BANK_STATEMENT";

export type DocumentStatus = "PENDING" | "VERIFIED" | "REJECTED";

export const DOCUMENT_TYPES: DocumentType[] = ["PAN", "IDENTITY_PROOF", "ADDRESS_PROOF", "BANK_STATEMENT"];

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  PAN: "PAN Card",
  IDENTITY_PROOF: "Identity Proof",
  ADDRESS_PROOF: "Address Proof",
  BANK_STATEMENT: "Bank Statement",
};

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

/** The customer-facing view of the caller's own application — never carries another customer's data (see CurrentUser server-side). */
export interface KycApplication {
  id: string;
  status: KycStatus;
  panNumber: string;
  occupation: string;
  annualIncomeRange: string;
  submittedAt: string | null;
  reviewedAt: string | null;
  reviewReason: string | null;
  missingDocumentTypes: DocumentType[];
  documents: KycDocument[];
  createdAt: string;
  updatedAt: string;
}

/** No `customerId` field — the owner is always the authenticated caller, derived from the JWT server-side. */
export interface CreateKycApplicationRequest {
  panNumber: string;
  occupation: string;
  annualIncomeRange: string;
}
