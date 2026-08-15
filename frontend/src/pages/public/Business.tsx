import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/images/coming-soon.svg";

export default function Business() {
  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <ComingSoonPage
        title="Business Banking"
        description="Business current accounts, merchant payments, business loans, and trade finance for BankSphere business customers."
        illustration={illustration}
        plannedFeatures={[
          "Business current accounts with dedicated support",
          "Business loans and working capital financing",
          "Merchant payment acceptance and cash management",
          "Trade finance for import/export businesses",
        ]}
      />
    </div>
  );
}
