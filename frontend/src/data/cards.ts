export type CardTheme = "platinum" | "cashback" | "travel" | "rewards" | "debit";

export interface CardProduct {
  slug: string;
  theme: CardTheme;
  name: string;
  tagline: string;
  network: "VISA" | "Mastercard" | "RuPay";
  annualFee: number;
  annualFeeWaiver?: string;
  benefits: string[];
  eligibility: string[];
  documents: string[];
}

/**
 * All fees/benefits are fictional BankSphere demo products — see the
 * disclaimer rendered wherever this data is displayed
 * (components/banking/CardProductCard.tsx, ProductDetailLayout usage).
 * Never present as real market rates. Keep values here, not scattered
 * through JSX, so the disclaimer only has to be correct in one place.
 */
export const CARD_PRODUCTS: CardProduct[] = [
  {
    slug: "platinum",
    theme: "platinum",
    name: "BankSphere Platinum Card",
    tagline: "Premium rewards and lifestyle benefits.",
    network: "VISA",
    annualFee: 1499,
    annualFeeWaiver: "Waived on annual spend above ₹3,00,000",
    benefits: [
      "Reward points on every purchase",
      "Complimentary airport lounge access",
      "Curated dining offers at partner restaurants",
      "Enhanced purchase protection cover",
    ],
    eligibility: [
      "Age 21–65",
      "Minimum annual income ₹6,00,000",
      "Good credit history",
    ],
    documents: ["Government-issued photo ID", "Proof of address", "Income proof (last 3 months)"],
  },
  {
    slug: "cashback",
    theme: "cashback",
    name: "BankSphere Cashback Card",
    tagline: "Everyday cashback, no complicated categories.",
    network: "RuPay",
    annualFee: 999,
    annualFeeWaiver: "Waived in the first year",
    benefits: [
      "Flat cashback on everyday spends",
      "Extra cashback on online shopping",
      "Fuel surcharge waiver at partner stations",
      "No minimum redemption threshold",
    ],
    eligibility: [
      "Age 21–60",
      "Minimum annual income ₹3,00,000",
      "Good credit history",
    ],
    documents: ["Government-issued photo ID", "Proof of address", "Income proof (last 3 months)"],
  },
  {
    slug: "travel",
    theme: "travel",
    name: "BankSphere Travel Card",
    tagline: "Travel-focused rewards for the way you move.",
    network: "VISA",
    annualFee: 1999,
    annualFeeWaiver: "Waived on annual spend above ₹4,00,000",
    benefits: [
      "Accelerated travel rewards",
      "Airport lounge access, domestic and international",
      "Zero foreign transaction markup on eligible spends",
      "Complimentary travel insurance cover",
    ],
    eligibility: [
      "Age 21–65",
      "Minimum annual income ₹7,50,000",
      "Good credit history",
    ],
    documents: ["Government-issued photo ID", "Proof of address", "Income proof (last 3 months)", "Passport copy (recommended)"],
  },
  {
    slug: "rewards",
    theme: "rewards",
    name: "BankSphere Rewards Card",
    tagline: "Earn more on the categories you spend the most.",
    network: "Mastercard",
    annualFee: 1299,
    annualFeeWaiver: "Waived on annual spend above ₹2,50,000",
    benefits: [
      "Bonus reward points on dining and groceries",
      "Milestone spend benefits",
      "Partner merchant offers",
      "Flexible redemption catalog",
    ],
    eligibility: [
      "Age 21–60",
      "Minimum annual income ₹4,00,000",
      "Good credit history",
    ],
    documents: ["Government-issued photo ID", "Proof of address", "Income proof (last 3 months)"],
  },
  {
    slug: "debit",
    theme: "debit",
    name: "BankSphere Debit Card",
    tagline: "Comes with every BankSphere savings and current account.",
    network: "RuPay",
    annualFee: 0,
    benefits: [
      "Free with every BankSphere account",
      "Nationwide ATM access",
      "Contactless tap-to-pay",
      "Instant spend notifications",
    ],
    eligibility: ["Issued automatically with an active BankSphere account"],
    documents: [],
  },
];

export function getCardBySlug(slug: string): CardProduct | undefined {
  return CARD_PRODUCTS.find((card) => card.slug === slug);
}
