export interface Faq {
  id: string;
  question: string;
  answer: string;
}

export const FAQS: Faq[] = [
  {
    id: "open-account",
    question: "How do I open an account?",
    answer:
      "Reach out through the Contact page and our team will help you get started. Self-service account opening isn't available yet in this phase of BankSphere.",
  },
  {
    id: "savings-accounts",
    question: "What savings accounts are available?",
    answer:
      "BankSphere offers Savings and Current accounts today, with Fixed and Recurring Deposits alongside them. See the Accounts and Deposits pages for details.",
  },
  {
    id: "what-is-fd",
    question: "What is a Fixed Deposit?",
    answer:
      "A Fixed Deposit lets you invest a lump sum for a fixed tenure at a fixed interest rate, with guaranteed returns at maturity. See the Deposits page for BankSphere's illustrative demo rates.",
  },
  {
    id: "loan-rates",
    question: "What are the BankSphere demo loan rates?",
    answer:
      "Illustrative starting rates range from 8.50% p.a. for Home Loans to 10.99% p.a. for Personal Loans. These are fictional demo rates for this portfolio project, not real market rates — see the Loans page for the full breakdown.",
  },
  {
    id: "contact-support",
    question: "How do I contact support?",
    answer: "Visit the Contact page for email and phone details, or use the BankSphere Assistant chat button in the bottom-right corner of the screen.",
  },
  {
    id: "block-card",
    question: "How do I block my card?",
    answer:
      "Card management isn't implemented yet in this phase — it's on the roadmap under the Cards section of internet banking, currently shown as a \"coming soon\" area.",
  },
  {
    id: "view-transactions",
    question: "How do I view transactions?",
    answer:
      "Sign in and open Transactions from the banking sidebar, or open any account's details page to see its recent activity alongside deposit and withdrawal options.",
  },
];
