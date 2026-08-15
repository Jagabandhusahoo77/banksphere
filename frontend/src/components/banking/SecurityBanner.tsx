import Icon, { type IconName } from "@/components/common/Icon";

interface SecurityPoint {
  icon: IconName;
  title: string;
  description: string;
}

const DEFAULT_POINTS: SecurityPoint[] = [
  {
    icon: "shield-check",
    title: "Secure authentication",
    description: "Every session is tied to a verified customer identity before any account data loads.",
  },
  {
    icon: "eye-off",
    title: "Encrypted communication",
    description: "Traffic between your device and BankSphere is encrypted in transit end-to-end.",
  },
  {
    icon: "trending-up",
    title: "Transaction monitoring",
    description: "Every deposit and withdrawal is recorded to an auditable transaction ledger.",
  },
  {
    icon: "alert-circle",
    title: "Fraud protection",
    description: "Unusual account activity is designed to be reviewable and traceable, end to end.",
  },
];

export default function SecurityBanner({ points = DEFAULT_POINTS, compact = false }: { points?: SecurityPoint[]; compact?: boolean }) {
  return (
    <div className={`grid grid-cols-1 ${compact ? "sm:grid-cols-2" : "sm:grid-cols-2 lg:grid-cols-4"} gap-5`}>
      {points.map((point) => (
        <div key={point.title} className="flex flex-col gap-3">
          <span className="flex items-center justify-center w-11 h-11 rounded-full bg-white/10 text-white">
            <Icon name={point.icon} size={20} />
          </span>
          <div>
            <p className="text-body font-semibold text-white">{point.title}</p>
            <p className="mt-1 text-body-sm text-white/70">{point.description}</p>
          </div>
        </div>
      ))}
    </div>
  );
}
