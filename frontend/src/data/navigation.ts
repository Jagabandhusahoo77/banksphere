import type { IconName } from "@/components/common/Icon";

export interface NavLinkItem {
  label: string;
  to: string;
  icon?: IconName;
}

export interface NavColumn {
  heading: string;
  items: NavLinkItem[];
}

export interface PrimaryNavItem {
  label: string;
  /** Plain link — set for items with no mega menu (About Us, Help & Support). */
  to?: string;
  /** Present for items that open a mega menu instead of navigating directly. */
  megaMenu?: NavColumn[];
}

/**
 * Single source of truth for the public site's primary navigation —
 * consumed by MegaMenuPanel (desktop), PublicNavDrawer (mobile), and
 * Footer.tsx's Products column, so a link target is only ever defined
 * once. Business/NRI/Premium Banking items all route to a single honest
 * ComingSoonPage per section rather than fabricated product catalogs —
 * see docs/architecture/decisions and docs/frontend/routing.md.
 */
export const PRIMARY_NAV: PrimaryNavItem[] = [
  {
    label: "Personal",
    megaMenu: [
      {
        heading: "Accounts",
        items: [
          { label: "Savings Account", to: "/savings-account", icon: "wallet" },
          { label: "Current Account", to: "/account-types", icon: "wallet" },
          { label: "Salary Account", to: "/account-types", icon: "wallet" },
        ],
      },
      {
        heading: "Cards",
        items: [
          { label: "Credit Cards", to: "/cards", icon: "credit-card" },
          { label: "Debit Cards", to: "/cards/debit", icon: "credit-card" },
          { label: "Premium Cards", to: "/cards/platinum", icon: "credit-card" },
        ],
      },
      {
        heading: "Loans",
        items: [
          { label: "Personal Loan", to: "/loans/personal", icon: "percent" },
          { label: "Home Loan", to: "/loans/home", icon: "home" },
          { label: "Car Loan", to: "/loans/car", icon: "car" },
          { label: "Education Loan", to: "/loans/education", icon: "graduation-cap" },
        ],
      },
      {
        heading: "Deposits",
        items: [
          { label: "Fixed Deposit", to: "/deposits/fixed", icon: "trending-up" },
          { label: "Recurring Deposit", to: "/deposits/recurring", icon: "trending-up" },
        ],
      },
      {
        heading: "Investments",
        items: [
          { label: "Mutual Funds", to: "/wealth", icon: "trending-up" },
          { label: "Bonds", to: "/wealth", icon: "trending-up" },
        ],
      },
    ],
  },
  {
    label: "Business",
    megaMenu: [
      {
        heading: "Business Banking",
        items: [
          { label: "Business Current Account", to: "/business", icon: "building" },
          { label: "Business Loans", to: "/business", icon: "percent" },
          { label: "Merchant Payments", to: "/business", icon: "credit-card" },
          { label: "Trade Finance", to: "/business", icon: "globe" },
        ],
      },
    ],
  },
  {
    label: "NRI",
    megaMenu: [
      {
        heading: "NRI Banking",
        items: [
          { label: "NRE Account", to: "/nri", icon: "wallet" },
          { label: "NRO Account", to: "/nri", icon: "wallet" },
          { label: "NRI Deposits", to: "/nri", icon: "trending-up" },
          { label: "Remittances", to: "/nri", icon: "globe" },
        ],
      },
    ],
  },
  {
    label: "Premium Banking",
    megaMenu: [
      {
        heading: "Premium Banking",
        items: [
          { label: "Priority Banking", to: "/premium-banking", icon: "star" },
          { label: "Wealth Management", to: "/premium-banking", icon: "trending-up" },
          { label: "Private Banking Services", to: "/premium-banking", icon: "shield-check" },
        ],
      },
    ],
  },
  { label: "About Us", to: "/about" },
  { label: "Help & Support", to: "/help" },
];

export interface QuickCategory {
  icon: IconName;
  label: string;
  description: string;
  /** A route, or an in-page anchor on the homepage (e.g. "/#exclusive-offers"). */
  to: string;
}

/** Homepage "Quick Banking Categories" row, directly under the hero. */
export const QUICK_CATEGORIES: QuickCategory[] = [
  { icon: "wallet", label: "Accounts", description: "Savings accounts for everyday banking", to: "/savings-account" },
  { icon: "credit-card", label: "Cards", description: "Credit, debit and premium cards", to: "/cards" },
  { icon: "percent", label: "Loans", description: "Home, personal, car and education", to: "/loans" },
  { icon: "trending-up", label: "Investments", description: "Mutual funds and bonds", to: "/wealth" },
  { icon: "umbrella", label: "Insurance", description: "Protection for what matters", to: "/insurance" },
  { icon: "arrow-up-right", label: "Payments", description: "UPI, transfers and bill pay", to: "/#digital-banking-services" },
  { icon: "tag", label: "Offers", description: "Exclusive BankSphere deals", to: "/#exclusive-offers" },
];
