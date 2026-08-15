import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/images/coming-soon.svg";

export default function Insurance() {
  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <ComingSoonPage
        title="Insurance"
        description="Protection for what matters — life, health, and asset insurance are on our roadmap."
        illustration={illustration}
        plannedFeatures={[
          "Life insurance plans",
          "Health insurance plans",
          "Asset and property insurance",
        ]}
      />
    </div>
  );
}
