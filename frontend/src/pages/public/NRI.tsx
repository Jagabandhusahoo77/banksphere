import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/images/coming-soon.svg";

export default function NRI() {
  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <ComingSoonPage
        title="NRI Banking"
        description="Banking designed for Non-Resident Indians — NRE and NRO accounts, deposits, and remittances back home."
        illustration={illustration}
        plannedFeatures={[
          "NRE and NRO accounts for managing income in India",
          "NRI fixed and recurring deposits",
          "International remittances to and from India",
          "NRI-specific loan and investment products",
        ]}
      />
    </div>
  );
}
