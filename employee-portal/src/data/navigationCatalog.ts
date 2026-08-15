/**
 * Every entry the signed-in employee's real permission set (from their own
 * JWT/profile, never hardcoded per role name) doesn't unlock is filtered
 * out entirely in AppLayout via AuthContext.hasPermission — never shown,
 * disabled, or hinted at. Entries with a `route` are real, working pages
 * (Phase 9B added Cash Operations, the first one); entries without one are
 * rendered as an inert, clearly-labeled "Coming soon" item — never a link
 * to a page that doesn't exist yet. See ADR-007.
 */
export interface ModuleCatalogEntry {
  label: string;
  permission: string;
  route?: string;
  /**
   * Phase 9C — first use of nested nav entries (KYC & Compliance is the
   * first module with more than one real page). Each child is filtered
   * by the caller's own permission exactly like a top-level entry — an
   * employee holding KYC_VIEW but not KYC_REVIEW sees "KYC Queue" and
   * "Completed Reviews" but not "Document Verification." "Review
   * Application" is deliberately not a nav destination of its own: it
   * has no meaning without a specific application id, so it's reached by
   * selecting a row in either queue view, not from the sidebar.
   */
  children?: ModuleCatalogEntry[];
}

export const MODULE_CATALOG: ModuleCatalogEntry[] = [
  { label: "Customer 360", permission: "CUSTOMER_VIEW", route: "/customers" },
  { label: "Cash Operations", permission: "CASH_DEPOSIT", route: "/cash-operations" },
  {
    label: "KYC & Compliance",
    permission: "KYC_VIEW",
    route: "/kyc/queue",
    children: [
      { label: "KYC Queue", permission: "KYC_VIEW", route: "/kyc/queue" },
      { label: "Document Verification", permission: "KYC_REVIEW", route: "/kyc/under-review" },
      { label: "Completed Reviews", permission: "KYC_VIEW", route: "/kyc/completed" },
    ],
  },
  { label: "Loan Review", permission: "LOAN_VIEW" },
  { label: "Card Review", permission: "CARD_VIEW" },
  { label: "Service Requests", permission: "SERVICE_REQUEST_VIEW" },
  { label: "Transaction Investigation", permission: "TRANSACTION_INVESTIGATE" },
  { label: "Audit Trail", permission: "AUDIT_VIEW" },
  { label: "Employee Administration", permission: "EMPLOYEE_MANAGE" },
];
