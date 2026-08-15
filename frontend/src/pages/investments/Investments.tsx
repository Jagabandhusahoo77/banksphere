import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/illustrations/investments/investment-growth.svg";

export default function Investments() {
  return (
    <ComingSoonPage
      title="Investments"
      description="Goal-based investing, built into the same place you already bank."
      illustration={illustration}
      plannedFeatures={[
        "Start a recurring investment linked to your savings account",
        "Track portfolio performance over time",
        "Set and monitor progress toward savings goals",
      ]}
    />
  );
}
