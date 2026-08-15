export interface Offer {
  id: string;
  title: string;
  description: string;
  category: "Cards" | "Deposits" | "Accounts" | "Travel";
}

/** Fictional BankSphere demo offers — not real promotions. */
export const OFFERS: Offer[] = [
  {
    id: "offer-online-merchants",
    title: "10% off",
    description: "On selected online merchants with a BankSphere card.",
    category: "Cards",
  },
  {
    id: "offer-weekend-rewards",
    title: "2X Rewards",
    description: "On weekend spending with any BankSphere credit card.",
    category: "Cards",
  },
  {
    id: "offer-digital-fd",
    title: "Special FD benefit",
    description: "An extra rate benefit on eligible digital fixed deposits.",
    category: "Deposits",
  },
  {
    id: "offer-travel-benefits",
    title: "Travel benefits",
    description: "Lounge access and travel offers on selected BankSphere cards.",
    category: "Travel",
  },
];
