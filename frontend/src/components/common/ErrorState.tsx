import Icon from "./Icon";
import Button from "./Button";

interface ErrorStateProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

/**
 * Full-block error presentation for a failed data fetch. For inline
 * field-level or form-level errors, use each form component's own `error`
 * prop instead (see Input/Select), not this component.
 */
export default function ErrorState({ title = "Something went wrong", message, onRetry }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center text-center py-10 px-4 bg-semantic-error-light rounded-lg border border-semantic-error/20"
    >
      <span className="flex items-center justify-center w-12 h-12 rounded-full bg-white text-semantic-error mb-4">
        <Icon name="alert-circle" size={24} />
      </span>
      <p className="text-h3 text-ink-primary">{title}</p>
      <p className="text-body-sm text-ink-secondary mt-1.5 max-w-sm">{message}</p>
      {onRetry && (
        <div className="mt-5">
          <Button variant="outline" size="sm" onClick={onRetry}>
            Try again
          </Button>
        </div>
      )}
    </div>
  );
}
