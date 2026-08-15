import categoryDigitalBanking from "@/assets/illustrations/insights/digital-banking-category.svg";
import categoryPersonalFinance from "@/assets/illustrations/insights/digital-banking-personal-finance.svg";
import categoryInvestments from "@/assets/illustrations/insights/digital-banking-investments.svg";
import categorySaving from "@/assets/illustrations/insights/digital-banking-saving.svg";
import categorySecurity from "@/assets/illustrations/security/security-shield.svg";

export type InsightCategory = "Digital Banking" | "Personal Finance" | "Investments" | "Saving" | "Security";

export interface Insight {
  id: string;
  category: InsightCategory;
  title: string;
  summary: string;
  /** Static display string, not generated at render time. */
  date: string;
  readingTimeMinutes: number;
  image: string;
}

/**
 * Static demo editorial content for the homepage's "Latest Insights"
 * section — explainer-style, not breaking news, and not attributed to any
 * real publication. There is no article backend, so InsightCard's CTA
 * opens a "not published yet" Modal rather than linking anywhere.
 */
export const INSIGHTS: Insight[] = [
  {
    id: "insight-upi-explained",
    category: "Digital Banking",
    title: "Understanding UPI: how instant payments work",
    summary:
      "A plain-language look at how UPI moves money between bank accounts in seconds, and what happens behind the scenes.",
    date: "12 Aug 2026",
    readingTimeMinutes: 4,
    image: categoryDigitalBanking,
  },
  {
    id: "insight-savings-habits",
    category: "Personal Finance",
    title: "Five habits for building a savings cushion",
    summary: "Small, consistent habits that add up — from automating transfers to setting a realistic monthly target.",
    date: "8 Aug 2026",
    readingTimeMinutes: 5,
    image: categoryPersonalFinance,
  },
  {
    id: "insight-fd-laddering",
    category: "Investments",
    title: "What a Fixed Deposit ladder is, and when to use one",
    summary: "Spreading deposits across different maturities can balance returns with access to your money.",
    date: "3 Aug 2026",
    readingTimeMinutes: 6,
    image: categoryInvestments,
  },
  {
    id: "insight-reading-statements",
    category: "Saving",
    title: "Reading your account statement, line by line",
    summary: "What each entry on a bank statement actually means, so nothing on it is a mystery.",
    date: "29 Jul 2026",
    readingTimeMinutes: 3,
    image: categorySaving,
  },
  {
    id: "insight-two-factor",
    category: "Security",
    title: "Why two-factor login matters for your accounts",
    summary: "A short explanation of what two-factor authentication protects against, and why it's worth the extra step.",
    date: "22 Jul 2026",
    readingTimeMinutes: 4,
    image: categorySecurity,
  },
];
