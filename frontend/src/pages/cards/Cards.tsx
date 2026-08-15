import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/illustrations/cards/card-showcase.svg";

export default function Cards() {
  return (
    <ComingSoonPage
      title="Cards"
      description="Debit and credit cards for your BankSphere accounts, manageable entirely from your dashboard."
      illustration={illustration}
      plannedFeatures={[
        "Apply for a debit or credit card linked to your account",
        "Freeze, unfreeze, or set spending limits instantly",
        "View card transactions alongside your account history",
      ]}
    />
  );
}
