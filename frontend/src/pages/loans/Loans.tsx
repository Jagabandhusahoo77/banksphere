import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/illustrations/loans/loan-growth.svg";

export default function Loans() {
  return (
    <ComingSoonPage
      title="Loans"
      description="Personal, home and car loans with clear terms and fast decisions."
      illustration={illustration}
      plannedFeatures={[
        "Check your eligibility before you apply",
        "Track a loan application from submission to approval",
        "View repayment schedules alongside your accounts",
      ]}
    />
  );
}
