import Icon from "./Icon";

interface EmptyStateProps {
  title: string;
  message?: string;
}

/**
 * Deliberately no illustration (unlike the customer portal's EmptyState) —
 * an internal operations tool reads as more utilitarian, not marketing-styled.
 */
export default function EmptyState({ title, message }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center text-center py-10 px-4">
      <span className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-surface-muted text-ink-muted mb-4">
        <Icon name="alert-circle" size={22} />
      </span>
      <p className="text-h3 text-ink-primary">{title}</p>
      {message && <p className="text-body-sm text-ink-secondary mt-1.5 max-w-sm">{message}</p>}
    </div>
  );
}
