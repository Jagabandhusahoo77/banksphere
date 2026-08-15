import { useMemo, useState } from "react";
import Card from "@/components/common/Card";
import Input from "@/components/common/Input";
import { formatINR } from "@/utils/format";

interface FixedDepositCalculatorProps {
  defaultAmount?: number;
  defaultRate?: number;
  defaultTenureYears?: number;
}

/**
 * Annual-compounding maturity calculation:
 *   Maturity = P * (1 + r/100)^t
 * Same floating-point caveat as LoanCalculator.tsx applies — see that
 * file's comment. Rounded to whole rupees before display.
 */
function calculateMaturity(principal: number, annualRatePercent: number, tenureYears: number) {
  if (principal <= 0 || annualRatePercent < 0 || tenureYears <= 0) {
    return { maturityAmount: principal, interestEarned: 0 };
  }

  const maturityAmount = principal * Math.pow(1 + annualRatePercent / 100, tenureYears);
  const interestEarned = maturityAmount - principal;

  return {
    maturityAmount: Math.round(maturityAmount),
    interestEarned: Math.round(interestEarned),
  };
}

export default function FixedDepositCalculator({
  defaultAmount = 100000,
  defaultRate = 7.25,
  defaultTenureYears = 5,
}: FixedDepositCalculatorProps) {
  const [amount, setAmount] = useState(defaultAmount);
  const [rate, setRate] = useState(defaultRate);
  const [tenureYears, setTenureYears] = useState(defaultTenureYears);

  const { maturityAmount, interestEarned } = useMemo(
    () => calculateMaturity(amount, rate, tenureYears),
    [amount, rate, tenureYears],
  );

  return (
    <Card title="Returns calculator" subtitle="Estimate your maturity amount on a fixed deposit.">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <Input
          label="Deposit amount (₹)"
          type="number"
          min={1000}
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
          max={10}
          value={tenureYears}
          onChange={(e) => setTenureYears(Number(e.target.value) || 0)}
        />
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-surface-muted rounded-lg p-4">
          <p className="text-caption text-ink-muted">Principal</p>
          <p className="mt-1 text-h3 text-ink-primary">{formatINR(amount)}</p>
        </div>
        <div className="bg-surface-muted rounded-lg p-4">
          <p className="text-caption text-ink-muted">Interest earned</p>
          <p className="mt-1 text-h3 text-semantic-success">{formatINR(interestEarned)}</p>
        </div>
        <div className="bg-brand-primary-light rounded-lg p-4">
          <p className="text-caption text-ink-muted">Maturity amount</p>
          <p className="mt-1 text-h3 text-brand-primary">{formatINR(maturityAmount)}</p>
        </div>
      </div>

      <p className="mt-5 text-caption text-ink-muted">
        Illustrative BankSphere demo calculation, compounded annually. Actual maturity value depends on the
        deposit's exact compounding frequency and final booked rate — this estimate is for demonstration only.
      </p>
    </Card>
  );
}
