import { forwardRef, useId, type SelectHTMLAttributes } from "react";
import Icon from "./Icon";

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  hint?: string;
  error?: string;
}

const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, hint, error, id, className = "", children, ...rest },
  ref,
) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  const hintId = hint ? `${selectId}-hint` : undefined;
  const errorId = error ? `${selectId}-error` : undefined;

  return (
    <div className="w-full">
      {label && (
        <label htmlFor={selectId} className="block text-label text-ink-secondary mb-1.5">
          {label}
        </label>
      )}
      <div className="relative">
        <select
          ref={ref}
          id={selectId}
          aria-invalid={Boolean(error) || undefined}
          aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
          className={`w-full h-11 pl-3.5 pr-9 text-body text-ink-primary bg-white border rounded-md appearance-none transition-colors ${
            error ? "border-semantic-error focus-visible:ring-semantic-error" : "border-surface-border focus-visible:border-brand-primary"
          } disabled:bg-surface-muted disabled:text-ink-muted ${className}`}
          {...rest}
        >
          {children}
        </select>
        <Icon
          name="chevron-down"
          size={16}
          className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-ink-muted"
        />
      </div>
      {hint && !error && (
        <p id={hintId} className="mt-1.5 text-caption text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1.5 text-caption text-semantic-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
});

export default Select;
