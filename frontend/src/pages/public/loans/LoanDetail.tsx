import { useParams } from "react-router-dom";
import { getLoanBySlug } from "@/data/loans";
import ProductDetailLayout from "@/components/banking/ProductDetailLayout";
import LoanCalculator from "@/components/banking/LoanCalculator";
import NotFound from "@/pages/public/NotFound";

import illustration from "@/assets/illustrations/loans/loan-growth.svg";

function parseStartingRate(rateLabel: string): number {
  const match = rateLabel.match(/[\d.]+/);
  return match ? Number(match[0]) : 9;
}

export default function LoanDetail() {
  const { slug } = useParams<{ slug: string }>();
  const loan = slug ? getLoanBySlug(slug) : undefined;

  if (!loan) {
    return <NotFound />;
  }

  return (
    <ProductDetailLayout
      eyebrow="BankSphere Loans"
      title={loan.name}
      description={loan.description}
      image={illustration}
      keyFacts={[
        { label: "Starting rate", value: loan.startingRate },
        { label: "Loan amount", value: loan.maxAmount },
        { label: "Tenure", value: loan.maxTenure },
      ]}
      benefits={loan.benefits}
      eligibility={loan.eligibility}
      documents={loan.documents}
      ctaLabel="Apply for this loan"
      disclaimer="Illustrative BankSphere demo rate. Actual rates may vary — not real market rates."
      extra={
        <section>
          <h2 className="text-h2 text-ink-primary mb-5">Estimate your EMI</h2>
          <LoanCalculator defaultRate={parseStartingRate(loan.startingRate)} />
        </section>
      }
    />
  );
}
