import { Link } from "react-router-dom";
import type { LoanProduct } from "@/data/loans";
import Button from "@/components/common/Button";

import homePhoto from "@/assets/images/loans/home-loan.webp";
import personalPhoto from "@/assets/images/loans/personal-loan.webp";
import carPhoto from "@/assets/images/loans/car-loan.webp";
import educationPhoto from "@/assets/images/loans/education-loan.webp";

interface LoanVisual {
  src: string;
  alt: string;
  /** Tailwind object-position class — biases the crop toward the person, away from any baked-in text panel. */
  position: string;
}

const LOAN_VISUALS: Record<string, LoanVisual> = {
  home: { src: homePhoto, alt: "A couple standing in front of their new home, financed with a BankSphere Home Loan", position: "object-center" },
  personal: { src: personalPhoto, alt: "A man reviewing paperwork on a laptop while considering a BankSphere Personal Loan", position: "object-right" },
  car: { src: carPhoto, alt: "A family standing beside their new car, financed with a BankSphere Car Loan", position: "object-center" },
  education: { src: educationPhoto, alt: "A student studying with a laptop, funded by a BankSphere Education Loan", position: "object-right" },
};

export default function LoanProductCard({ loan }: { loan: LoanProduct }) {
  const visual = LOAN_VISUALS[loan.slug];

  return (
    <div className="flex flex-col bg-white rounded-lg border border-surface-border overflow-hidden transition-all duration-200 hover:-translate-y-1 hover:shadow-elevation-3">
      {visual && (
        <img
          src={visual.src}
          alt={visual.alt}
          loading="lazy"
          className={`w-full h-44 object-cover ${visual.position}`}
        />
      )}
      <div className="p-6 flex flex-col flex-1">
        <h3 className="text-h3 text-ink-primary">{loan.name}</h3>
        <p className="mt-1 text-body-sm text-ink-secondary">{loan.description}</p>

        <div className="mt-4 grid grid-cols-3 gap-2 text-center border-y border-surface-border py-3">
          <div>
            <p className="text-label text-ink-muted">Rate</p>
            <p className="text-body-sm font-semibold text-ink-primary">{loan.startingRate}</p>
          </div>
          <div>
            <p className="text-label text-ink-muted">Amount</p>
            <p className="text-body-sm font-semibold text-ink-primary">{loan.maxAmount}</p>
          </div>
          <div>
            <p className="text-label text-ink-muted">Tenure</p>
            <p className="text-body-sm font-semibold text-ink-primary">{loan.maxTenure}</p>
          </div>
        </div>

        <p className="mt-4 text-body-sm text-ink-secondary flex-1">{loan.benefits[0]}</p>

        <Link to={`/loans/${loan.slug}`} className="mt-5">
          <Button variant="outline" fullWidth>
            Explore {loan.name}
          </Button>
        </Link>
        <p className="mt-3 text-caption text-ink-muted">Illustrative BankSphere demo rate. Actual rates may vary.</p>
      </div>
    </div>
  );
}
