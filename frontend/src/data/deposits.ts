export interface DepositProduct {
  slug: string;
  name: string;
  description: string;
  rate: string;
  tenure: string;
  minimumDeposit: string;
  benefits: string[];
  eligibility: string[];
  documents: string[];
}

/** Illustrative BankSphere demo rates — never real ICICI or market rates. */
export const DEPOSIT_PRODUCTS: DepositProduct[] = [
  {
    slug: "fixed",
    name: "Fixed Deposit",
    description: "Lock in a rate and grow your savings with a fixed tenure deposit.",
    rate: "Up to 7.25% p.a.",
    tenure: "7 days – 10 years",
    minimumDeposit: "₹1,000",
    benefits: [
      "Guaranteed returns for the full tenure",
      "Flexible tenure from 7 days to 10 years",
      "Senior citizen rate benefit",
      "Loan against FD available",
    ],
    eligibility: ["Indian resident individual", "Existing or new BankSphere customer"],
    documents: ["Government-issued photo ID", "Proof of address"],
  },
  {
    slug: "recurring",
    name: "Recurring Deposit",
    description: "Build a savings habit with fixed monthly contributions.",
    rate: "Up to 6.75% p.a.",
    tenure: "6 months – 10 years",
    minimumDeposit: "₹500 per month",
    benefits: [
      "Disciplined monthly savings",
      "Flexible tenure from 6 months to 10 years",
      "Auto-debit from your BankSphere account",
      "Premature withdrawal available",
    ],
    eligibility: ["Indian resident individual", "Existing or new BankSphere customer"],
    documents: ["Government-issued photo ID", "Proof of address"],
  },
];

export function getDepositBySlug(slug: string): DepositProduct | undefined {
  return DEPOSIT_PRODUCTS.find((deposit) => deposit.slug === slug);
}
