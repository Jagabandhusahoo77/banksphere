import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/images/coming-soon.svg";

export default function PremiumBanking() {
  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <ComingSoonPage
        title="Premium Banking"
        description="Priority banking, wealth management, and private banking services for BankSphere's premium customers."
        illustration={illustration}
        plannedFeatures={[
          "Priority Banking with a dedicated relationship manager",
          "Wealth management and portfolio planning",
          "Private banking services for high-net-worth customers",
          "Preferential rates on eligible products",
        ]}
      />
    </div>
  );
}
