export interface LoanProduct {
  slug: string;
  name: string;
  description: string;
  startingRate: string;
  maxAmount: string;
  maxTenure: string;
  benefits: string[];
  eligibility: string[];
  documents: string[];
}

/**
 * Illustrative BankSphere demo rates — never real ICICI or market rates.
 * Every place this data is rendered must also render the disclaimer
 * (see LoanCalculator.tsx and ProductDetailLayout usage).
 */
export const LOAN_PRODUCTS: LoanProduct[] = [
  {
    slug: "home",
    name: "Home Loan",
    description: "Financing for buying, building, or renovating your home.",
    startingRate: "8.50% p.a.",
    maxAmount: "Up to ₹5 Crore",
    maxTenure: "Up to 30 years",
    benefits: [
      "Competitive starting interest rate",
      "Flexible tenure up to 30 years",
      "Balance transfer option available",
      "Minimal paperwork for existing BankSphere customers",
    ],
    eligibility: ["Age 23–65 at loan maturity", "Stable income source", "Good credit history"],
    documents: ["Government-issued photo ID", "Proof of address", "Income proof", "Property documents"],
  },
  {
    slug: "personal",
    name: "Personal Loan",
    description: "Unsecured financing for the moments that can't wait.",
    startingRate: "10.99% p.a.",
    maxAmount: "Up to ₹25 Lakh",
    maxTenure: "Up to 7 years",
    benefits: [
      "No collateral required",
      "Fast, digital-first approval process",
      "Flexible repayment tenure",
      "No prepayment lock-in after 12 months",
    ],
    eligibility: ["Age 21–60", "Minimum annual income ₹3,00,000", "Good credit history"],
    documents: ["Government-issued photo ID", "Proof of address", "Income proof (last 3 months)"],
  },
  {
    slug: "car",
    name: "Car Loan",
    description: "Finance a new or pre-owned vehicle with a plan that fits your budget.",
    startingRate: "9.25% p.a.",
    maxAmount: "Up to ₹50 Lakh",
    maxTenure: "Up to 7 years",
    benefits: [
      "Financing for new and pre-owned vehicles",
      "Up to 90% on-road funding",
      "Quick approval for existing BankSphere customers",
      "Flexible EMI options",
    ],
    eligibility: ["Age 21–65 at loan maturity", "Stable income source", "Good credit history"],
    documents: ["Government-issued photo ID", "Proof of address", "Income proof", "Vehicle quotation"],
  },
  {
    slug: "education",
    name: "Education Loan",
    description: "Invest in your future or your family's, in India or abroad.",
    startingRate: "9.00% p.a.",
    maxAmount: "Up to ₹50 Lakh",
    maxTenure: "Flexible tenure",
    benefits: [
      "Covers tuition, travel, and living expenses",
      "Moratorium period during the course",
      "Available for domestic and international study",
      "Tax benefits under applicable law",
    ],
    eligibility: ["Indian resident", "Admission confirmation from a recognized institution", "Co-applicant required"],
    documents: ["Government-issued photo ID", "Admission letter", "Fee structure", "Co-applicant income proof"],
  },
];

export function getLoanBySlug(slug: string): LoanProduct | undefined {
  return LOAN_PRODUCTS.find((loan) => loan.slug === slug);
}
