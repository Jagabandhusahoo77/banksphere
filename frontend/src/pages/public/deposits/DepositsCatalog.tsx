import { Link } from "react-router-dom";
import { DEPOSIT_PRODUCTS } from "@/data/deposits";
import Button from "@/components/common/Button";
import FixedDepositCalculator from "@/components/banking/FixedDepositCalculator";
import illustration from "@/assets/illustrations/accounts/accounts-overview.svg";

export default function DepositsCatalog() {
  return (
    <div>
      <section className="bg-brand-primary-light">
        <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm grid grid-cols-1 md:grid-cols-2 gap-10 items-center">
          <div>
            <p className="text-label text-brand-primary uppercase tracking-wide">Deposits</p>
            <h1 className="mt-2 text-h1 text-ink-primary">Grow your savings with BankSphere Deposits</h1>
            <p className="mt-3 text-body text-ink-secondary max-w-lg">
              Lock in a rate with a Fixed Deposit, or build a savings habit with a Recurring Deposit.
            </p>
          </div>
          <div className="flex justify-center">
            <img src={illustration} alt="" className="w-full max-w-sm" />
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-content px-4 sm:px-6 py-section-sm">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          {DEPOSIT_PRODUCTS.map((deposit) => (
            <div key={deposit.slug} className="bg-white rounded-lg border border-surface-border p-6">
              <h3 className="text-h3 text-ink-primary">{deposit.name}</h3>
              <p className="mt-1 text-body-sm text-ink-secondary">{deposit.description}</p>
              <p className="mt-4 text-h1 text-brand-primary">{deposit.rate}<span className="text-body-sm text-ink-muted">*</span></p>
              <dl className="mt-3 space-y-1.5">
                <div className="flex justify-between text-body-sm">
                  <dt className="text-ink-muted">Tenure</dt>
                  <dd className="text-ink-primary font-medium">{deposit.tenure}</dd>
                </div>
                <div className="flex justify-between text-body-sm">
                  <dt className="text-ink-muted">Minimum deposit</dt>
                  <dd className="text-ink-primary font-medium">{deposit.minimumDeposit}</dd>
                </div>
              </dl>
              <div className="mt-5 flex flex-col sm:flex-row gap-2">
                <Link to={`/deposits/${deposit.slug}`} className="flex-1">
                  <Button variant="outline" fullWidth>
                    Calculate Returns
                  </Button>
                </Link>
                <Link to={`/deposits/${deposit.slug}`} className="flex-1">
                  <Button variant="primary" fullWidth>
                    Open a {deposit.name}
                  </Button>
                </Link>
              </div>
            </div>
          ))}
        </div>
        <p className="mt-6 text-caption text-ink-muted text-center">
          *Illustrative BankSphere demo rate. Actual rates may vary — not real market rates.
        </p>
      </section>

      <section className="mx-auto max-w-content px-4 sm:px-6 py-section-sm">
        <h2 className="text-h2 text-ink-primary mb-6">Try the returns calculator</h2>
        <FixedDepositCalculator />
      </section>
    </div>
  );
}
