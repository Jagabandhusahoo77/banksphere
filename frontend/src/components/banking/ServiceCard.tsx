import { Link } from "react-router-dom";
import Icon from "@/components/common/Icon";

interface ServiceCardProps {
  iconSrc: string;
  title: string;
  description: string;
  to: string;
  /** Optional standout benefit line, shown above the CTA for premium showcase use (e.g. Home's Popular Products). */
  benefit?: string;
}

export default function ServiceCard({ iconSrc, title, description, to, benefit }: ServiceCardProps) {
  return (
    <Link
      to={to}
      className="group flex flex-col bg-white rounded-lg border border-surface-border p-6 transition-all duration-200 hover:-translate-y-1 hover:border-brand-primary/40 hover:shadow-elevation-3"
    >
      <img src={iconSrc} alt="" className="w-14 h-14 mb-4" />
      <h3 className="text-h3 text-ink-primary">{title}</h3>
      <p className="mt-2 text-body-sm text-ink-secondary flex-1">{description}</p>
      {benefit && (
        <p className="mt-3 flex items-center gap-1.5 text-body-sm font-medium text-semantic-success">
          <Icon name="check-circle" size={16} />
          {benefit}
        </p>
      )}
      <span className="mt-4 inline-flex items-center gap-1.5 text-body-sm font-medium text-brand-primary">
        Learn more
        <Icon name="arrow-right" size={16} className="transition-transform group-hover:translate-x-0.5" />
      </span>
    </Link>
  );
}
