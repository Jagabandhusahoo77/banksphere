import { getAccountBySlug } from "@/data/accounts";
import ProductDetailLayout from "@/components/banking/ProductDetailLayout";
import NotFound from "@/pages/public/NotFound";

import illustration from "@/assets/illustrations/accounts/savings-account.svg";

export default function SavingsAccountDetail() {
  const account = getAccountBySlug("savings");

  if (!account) {
    return <NotFound />;
  }

  return (
    <ProductDetailLayout
      eyebrow="BankSphere Accounts"
      title={account.name}
      description={account.description}
      image={illustration}
      keyFacts={[
        { label: "Interest rate", value: account.rate },
        { label: "Minimum balance", value: account.minimumBalance },
      ]}
      benefits={account.benefits}
      eligibility={account.eligibility}
      documents={account.documents}
      ctaLabel="Open a Savings Account"
      disclaimer="Illustrative BankSphere demo rate. Actual rates may vary — not real market rates."
    />
  );
}
