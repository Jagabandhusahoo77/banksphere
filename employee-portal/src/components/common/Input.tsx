import { forwardRef, useId, type InputHTMLAttributes } from "react";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  hint?: string;
  error?: string;
  leadingText?: string;
}

const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, hint, error, leadingText, id, className = "", ...rest },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;

  return (
    <div className="w-full">
      {label && (
        <label htmlFor={inputId} className="block text-label text-ink-secondary mb-1.5">
          {label}
        </label>
      )}
      <div className="relative flex items-stretch">
        {leadingText && (
          <span className="inline-flex items-center px-3 rounded-l-md border border-r-0 border-surface-border bg-surface-muted text-ink-secondary text-body-sm">
            {leadingText}
          </span>
        )}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={Boolean(error) || undefined}
          aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
          className={`w-full h-11 px-3.5 text-body text-ink-primary bg-white border transition-colors placeholder:text-ink-muted ${
            leadingText ? "rounded-r-md" : "rounded-md"
          } ${
            error
              ? "border-semantic-error focus-visible:ring-semantic-error"
              : "border-surface-border focus-visible:border-brand-primary"
          } disabled:bg-surface-muted disabled:text-ink-muted ${className}`}
          {...rest}
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

export default Input;
