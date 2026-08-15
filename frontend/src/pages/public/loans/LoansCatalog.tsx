import { Link } from "react-router-dom";
import { LOAN_PRODUCTS } from "@/data/loans";
import Button from "@/components/common/Button";
import LoanCalculator from "@/components/banking/LoanCalculator";

import homeLoanIcon from "@/assets/icons/home-loan.svg";
import personalLoanIcon from "@/assets/icons/personal-loan.svg";
import carLoanIcon from "@/assets/icons/car-loan.svg";
import educationLoanIcon from "@/assets/icons/education-loan.svg";

const LOAN_ICONS: Record<string, string> = {
  home: homeLoanIcon,
  personal: personalLoanIcon,
  car: carLoanIcon,
  education: educationLoanIcon,
};

export default function LoansCatalog() {
  return (
    <div>
      <section className="bg-brand-primary-light">
        <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm text-center">
          <p className="text-label text-brand-primary uppercase tracking-wide">Loans</p>
          <h1 className="mt-2 text-h1 text-ink-primary">Loans designed around your goals</h1>
          <p className="mt-3 text-body text-ink-secondary max-w-xl mx-auto">
            Competitive starting rates and flexible tenure across home, personal, car and education financing.
          </p>
        </div>
      </section>

      <section className="mx-auto max-w-content px-4 sm:px-6 py-section-sm">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {LOAN_PRODUCTS.map((loan) => (
            <div key={loan.slug} className="bg-white rounded-lg border border-surface-border p-6 flex flex-col">
              <img src={LOAN_ICONS[loan.slug]} alt="" className="w-14 h-14 mb-4" />
              <h3 className="text-h3 text-ink-primary">{loan.name}</h3>
              <dl className="mt-4 space-y-2 flex-1">
                <div className="flex justify-between text-body-sm">
                  <dt className="text-ink-muted">Starting rate</dt>
                  <dd className="text-ink-primary font-medium">{loan.startingRate}</dd>
                </div>
                <div className="flex justify-between text-body-sm">
                  <dt className="text-ink-muted">Loan amount</dt>
                  <dd className="text-ink-primary font-medium">{loan.maxAmount}</dd>
                </div>
                <div className="flex justify-between text-body-sm">
                  <dt className="text-ink-muted">Tenure</dt>
                  <dd className="text-ink-primary font-medium">{loan.maxTenure}</dd>
                </div>
              </dl>
              <Link to={`/loans/${loan.slug}`} className="mt-5">
                <Button variant="outline" fullWidth>
                  Explore {loan.name}
                </Button>
              </Link>
            </div>
          ))}
        </div>
        <p className="mt-6 text-caption text-ink-muted text-center">
          Illustrative BankSphere demo rates. Actual rates may vary — these are not real market rates.
        </p>
      </section>

      <section className="mx-auto max-w-content px-4 sm:px-6 py-section-sm">
        <h2 className="text-h2 text-ink-primary mb-6">Try the EMI calculator</h2>
        <LoanCalculator />
      </section>
    </div>
  );
}
