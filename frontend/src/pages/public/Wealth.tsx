import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/illustrations/investments/investment-growth.svg";
import wealthPhoto from "@/assets/images/investments/wealth-investment.webp";

export default function Wealth() {
  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <div className="mb-10 rounded-2xl overflow-hidden shadow-elevation-2">
        <img
          src={wealthPhoto}
          alt="A BankSphere advisor reviewing an investment portfolio with a couple"
          loading="lazy"
          className="w-full h-64 sm:h-80 object-cover object-bottom"
        />
      </div>
      <ComingSoonPage
        title="Investments"
        description="Mutual funds and bonds, to help you grow wealth alongside your everyday banking."
        illustration={illustration}
        plannedFeatures={[
          "Curated mutual fund options across risk profiles",
          "Government and corporate bonds",
          "Goal-based investment planning tools",
        ]}
      />
    </div>
  );
}
