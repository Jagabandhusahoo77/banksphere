export interface AccountProduct {
  slug: string;
  name: string;
  description: string;
  rate: string;
  minimumBalance: string;
  benefits: string[];
  eligibility: string[];
  documents: string[];
}

/**
 * Illustrative BankSphere demo rate — never a real ICICI or market rate.
 * Deliberately a single product (Savings) — Current and Salary accounts
 * are covered by the honest /account-types "coming soon" page instead of
 * being fabricated here, since they don't exist as real backend products
 * yet (see docs/frontend/routing.md).
 */
export const ACCOUNT_PRODUCTS: AccountProduct[] = [
  {
    slug: "savings",
    name: "Savings Account",
    description: "A digital-first savings account for everyday banking and long-term saving alike.",
    rate: "Up to 4.00% p.a.",
    minimumBalance: "₹0 — no minimum balance",
    benefits: [
      "No minimum balance requirement",
      "Free BankSphere Debit Card",
      "Instant digital account opening",
      "24/7 mobile and internet banking access",
    ],
    eligibility: ["Indian resident individual", "Age 18 and above", "Valid government-issued ID"],
    documents: ["Government-issued photo ID", "Proof of address", "Recent photograph"],
  },
];

export function getAccountBySlug(slug: string): AccountProduct | undefined {
  return ACCOUNT_PRODUCTS.find((account) => account.slug === slug);
}
