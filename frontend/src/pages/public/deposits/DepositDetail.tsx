import { useParams } from "react-router-dom";
import { getDepositBySlug } from "@/data/deposits";
import ProductDetailLayout from "@/components/banking/ProductDetailLayout";
import FixedDepositCalculator from "@/components/banking/FixedDepositCalculator";
import NotFound from "@/pages/public/NotFound";

import illustration from "@/assets/illustrations/accounts/accounts-overview.svg";

function parseRate(rateLabel: string): number {
  const match = rateLabel.match(/[\d.]+/);
  return match ? Number(match[0]) : 7;
}

export default function DepositDetail() {
  const { slug } = useParams<{ slug: string }>();
  const deposit = slug ? getDepositBySlug(slug) : undefined;

  if (!deposit) {
    return <NotFound />;
  }

  return (
    <ProductDetailLayout
      eyebrow="BankSphere Deposits"
      title={deposit.name}
      description={deposit.description}
      image={illustration}
      keyFacts={[
        { label: "Rate", value: deposit.rate },
        { label: "Tenure", value: deposit.tenure },
        { label: "Minimum deposit", value: deposit.minimumDeposit },
      ]}
      benefits={deposit.benefits}
      eligibility={deposit.eligibility}
      documents={deposit.documents}
      ctaLabel={`Open a ${deposit.name}`}
      disclaimer="Illustrative BankSphere demo rate. Actual rates may vary — not real market rates."
      extra={
        <section>
          <h2 className="text-h2 text-ink-primary mb-5">Calculate your returns</h2>
          <FixedDepositCalculator defaultRate={parseRate(deposit.rate)} />
        </section>
      }
    />
  );
}
