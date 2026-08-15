import { useMemo, useState } from "react";
import Card from "@/components/common/Card";
import Input from "@/components/common/Input";
import { formatINR } from "@/utils/format";

interface LoanCalculatorProps {
  defaultAmount?: number;
  defaultRate?: number;
  defaultTenureYears?: number;
}

/**
 * Standard reducing-balance EMI calculation:
 *   EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)
 * where r is the monthly rate and n the tenure in months.
 *
 * This runs entirely client-side on plain JS numbers — there is no
 * BigDecimal available in the browser without adding a new dependency,
 * which this phase avoids. The one place that matters (never showing a
 * raw floating-point artifact like "12345.679999999") is handled by
 * always rounding through formatINR()/Math.round() before display,
 * never rendering an intermediate value directly.
 */
function calculateEmi(principal: number, annualRatePercent: number, tenureYears: number) {
  const monthlyRate = annualRatePercent / 12 / 100;
  const months = tenureYears * 12;

  if (principal <= 0 || annualRatePercent <= 0 || months <= 0) {
    return { emi: 0, totalPayable: 0, totalInterest: 0 };
  }

  const factor = Math.pow(1 + monthlyRate, months);
  const emi = (principal * monthlyRate * factor) / (factor - 1);
  const totalPayable = emi * months;
  const totalInterest = totalPayable - principal;

  return {
    emi: Math.round(emi),
    totalPayable: Math.round(totalPayable),
    totalInterest: Math.round(totalInterest),
  };
}

export default function LoanCalculator({ defaultAmount = 2500000, defaultRate = 8.5, defaultTenureYears = 20 }: LoanCalculatorProps) {
  const [amount, setAmount] = useState(defaultAmount);
  const [rate, setRate] = useState(defaultRate);
  const [tenureYears, setTenureYears] = useState(defaultTenureYears);

  const { emi, totalPayable, totalInterest } = useMemo(
    () => calculateEmi(amount, rate, tenureYears),
    [amount, rate, tenureYears],
  );

  return (
    <Card title="EMI calculator" subtitle="See an estimated monthly payment for this loan.">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <Input
          label="Loan amount (₹)"
          type="number"
          min={0}
          value={amount}
          onChange={(e) => setAmount(Number(e.target.value) || 0)}
        />
        <Input
          label="Interest rate (% p.a.)"
          type="number"
          min={0}
          step={0.05}
          value={rate}
          onChange={(e) => setRate(Number(e.target.value) || 0)}
        />
        <Input
          label="Tenure (years)"
          type="number"
          min={1}
          max={30}
          value={tenureYears}
          onChange={(e) => setTenureYears(Number(e.target.value) || 0)}
        />
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="bg-brand-primary-light rounded-lg p-4">
          <p className="text-caption text-ink-muted">Monthly EMI</p>
          <p className="mt-1 text-h3 text-brand-primary">{formatINR(emi)}</p>
        </div>
        <div className="bg-surface-muted rounded-lg p-4">
          <p className="text-caption text-ink-muted">Principal</p>
          <p className="mt-1 text-h3 text-ink-primary">{formatINR(amount)}</p>
        </div>
        <div className="bg-surface-muted rounded-lg p-4">
          <p className="text-caption text-ink-muted">Total interest</p>
          <p className="mt-1 text-h3 text-ink-primary">{formatINR(totalInterest)}</p>
        </div>
        <div className="bg-surface-muted rounded-lg p-4">
          <p className="text-caption text-ink-muted">Total payable</p>
          <p className="mt-1 text-h3 text-ink-primary">{formatINR(totalPayable)}</p>
        </div>
      </div>

      <p className="mt-5 text-caption text-ink-muted">
        Illustrative BankSphere demo calculation. Actual EMI depends on final approved rate, processing fees, and
        the lender's exact calculation method — this estimate is for demonstration only.
      </p>
    </Card>
  );
}
