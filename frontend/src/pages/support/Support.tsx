import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/images/coming-soon.svg";

export default function Support() {
  return (
    <ComingSoonPage
      title="Support"
      description="Service requests, FAQs, and help from a real person when you need it — right from your dashboard."
      illustration={illustration}
      plannedFeatures={[
        "Raise and track a service request without leaving the app",
        "Browse frequently asked questions by topic",
        "Chat with support during business hours",
      ]}
    />
  );
}
