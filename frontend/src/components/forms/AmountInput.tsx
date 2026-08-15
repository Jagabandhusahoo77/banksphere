import FormField from "./FormField";

interface AmountInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  currency: string;
  error?: string;
  hint?: string;
  disabled?: boolean;
}

/**
 * A currency amount field. Keeps the raw value as a string (the caller
 * decides when/how to parse it — see accountService's AmountRequest,
 * which takes a number) and only restricts input to a valid decimal
 * pattern client-side; the authoritative validation is still the backend's
 * @DecimalMin/@Digits rules on AmountRequest.
 */
export default function AmountInput({ label, value, onChange, currency, error, hint, disabled }: AmountInputProps) {
  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const next = event.target.value;
    if (next === "" || /^\d*\.?\d{0,2}$/.test(next)) {
      onChange(next);
    }
  };

  return (
    <FormField label={label} error={error} hint={hint}>
      {(fieldProps) => (
        <div className="relative flex items-stretch">
          <span className="inline-flex items-center px-3 rounded-l-md border border-r-0 border-surface-border bg-surface-muted text-ink-secondary text-body-sm">
            {currency}
          </span>
          <input
            {...fieldProps}
            type="text"
            inputMode="decimal"
            value={value}
            onChange={handleChange}
            disabled={disabled}
            placeholder="0.00"
            className={`w-full h-11 px-3.5 rounded-r-md text-body text-ink-primary bg-white border transition-colors placeholder:text-ink-muted ${
              error ? "border-semantic-error" : "border-surface-border focus-visible:border-brand-primary"
            } disabled:bg-surface-muted disabled:text-ink-muted`}
          />
        </div>
      )}
    </FormField>
  );
}
