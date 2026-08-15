import type { ReactNode } from "react";

interface CardProps {
  title?: string;
  subtitle?: string;
  action?: ReactNode;
  padding?: "none" | "compact" | "default";
  interactive?: boolean;
  className?: string;
  children: ReactNode;
}

const PADDING_CLASSES = {
  none: "",
  compact: "p-4",
  default: "p-5 sm:p-6",
};

export default function Card({
  title,
  subtitle,
  action,
  padding = "default",
  interactive = false,
  className = "",
  children,
}: CardProps) {
  return (
    <div
      className={`bg-white rounded-lg border border-surface-border shadow-elevation-1 ${PADDING_CLASSES[padding]} ${
        interactive ? "transition-shadow hover:shadow-elevation-2" : ""
      } ${className}`}
    >
      {(title || action) && (
        <div className="flex items-start justify-between mb-4 gap-3">
          <div>
            {title && <h3 className="text-h3 text-ink-primary">{title}</h3>}
            {subtitle && <p className="text-body-sm text-ink-secondary mt-0.5">{subtitle}</p>}
          </div>
          {action}
        </div>
      )}
      {children}
    </div>
  );
}
