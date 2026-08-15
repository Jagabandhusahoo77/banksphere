import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/images/coming-soon.svg";

export default function AccountTypes() {
  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <ComingSoonPage
        title="Current & Salary Accounts"
        description="Current and salary accounts are on our roadmap. In the meantime, explore our Savings Account — already open for business."
        illustration={illustration}
        plannedFeatures={[
          "Current accounts for frequent, high-volume transactions",
          "Salary accounts with employer-linked benefits",
          "Zero or reduced minimum balance options",
        ]}
      />
    </div>
  );
}
