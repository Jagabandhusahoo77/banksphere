import ComingSoonPage from "@/components/common/ComingSoonPage";
import illustration from "@/assets/illustrations/payments/payments-transfer.svg";

export default function Payments() {
  return (
    <ComingSoonPage
      title="Payments"
      description="Transfers, bill payments and UPI — all from one place. Deposits and withdrawals on your own accounts already work today from an account's details page."
      illustration={illustration}
      plannedFeatures={[
        "Transfer money to another BankSphere customer",
        "Pay bills directly from your account",
        "UPI payments linked to your BankSphere accounts",
      ]}
    />
  );
}
