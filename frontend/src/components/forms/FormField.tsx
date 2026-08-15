import { useId, type ReactNode } from "react";

interface FormFieldProps {
  label: string;
  hint?: string;
  error?: string;
  children: (fieldProps: { id: string; "aria-describedby"?: string; "aria-invalid"?: boolean }) => ReactNode;
}

/**
 * Label/hint/error chrome for a custom control that isn't Input or Select
 * (which already have this built in) — e.g. AmountInput. Passes the
 * generated id/aria attributes to the control via a render prop so they
 * stay correctly associated.
 */
export default function FormField({ label, hint, error, children }: FormFieldProps) {
  const generatedId = useId();
  const hintId = hint ? `${generatedId}-hint` : undefined;
  const errorId = error ? `${generatedId}-error` : undefined;

  return (
    <div className="w-full">
      <label htmlFor={generatedId} className="block text-label text-ink-secondary mb-1.5">
        {label}
      </label>
      {children({
        id: generatedId,
        "aria-describedby": [hintId, errorId].filter(Boolean).join(" ") || undefined,
        "aria-invalid": Boolean(error) || undefined,
      })}
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
}
