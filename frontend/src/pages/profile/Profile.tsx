import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/images/coming-soon.svg";

export default function Profile() {
  return (
    <ComingSoonPage
      title="Profile"
      description="Manage your personal details and preferences in one place."
      illustration={illustration}
      plannedFeatures={[
        "Update your contact details and communication preferences",
        "Manage how you sign in once BankSphere adds full authentication",
        "Review your linked accounts and cards",
      ]}
    />
  );
}
