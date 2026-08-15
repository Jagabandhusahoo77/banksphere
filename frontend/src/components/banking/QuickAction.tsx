import { Link } from "react-router-dom";
import Icon, { type IconName } from "@/components/common/Icon";

interface QuickActionProps {
  icon: IconName;
  label: string;
  to?: string;
  comingSoon?: boolean;
  onClick?: () => void;
}

export default function QuickAction({ icon, label, to, comingSoon = false, onClick }: QuickActionProps) {
  const content = (
    <>
      <span className="flex items-center justify-center w-11 h-11 rounded-full bg-brand-primary-light text-brand-primary">
        <Icon name={icon} size={20} />
      </span>
      <span className="text-body-sm font-medium text-ink-primary text-center">{label}</span>
      {comingSoon && <span className="text-caption text-ink-muted">Coming soon</span>}
    </>
  );

  const className =
    "flex flex-col items-center gap-2 rounded-lg border border-surface-border bg-white px-3 py-4 text-center transition-colors hover:border-brand-primary/40 hover:bg-brand-primary-light/40 w-full";

  if (comingSoon || !to) {
    return (
      <button type="button" onClick={onClick} className={className}>
        {content}
      </button>
    );
  }

  return (
    <Link to={to} className={className}>
      {content}
    </Link>
  );
}
