import type { ReactNode } from "react";
import emptyStateIllustration from "@/assets/images/empty-state.svg";

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: ReactNode;
  illustration?: string;
}

export default function EmptyState({ title, description, action, illustration }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center text-center py-10 px-4">
      <img src={illustration ?? emptyStateIllustration} alt="" className="w-32 h-auto mb-5" />
      <p className="text-h3 text-ink-primary">{title}</p>
      {description && <p className="text-body-sm text-ink-secondary mt-1.5 max-w-sm">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
