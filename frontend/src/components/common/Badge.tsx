import type { ReactNode } from "react";

type Tone = "success" | "warning" | "error" | "info" | "neutral" | "brand";

const TONE_CLASSES: Record<Tone, string> = {
  success: "bg-semantic-success-light text-semantic-success",
  warning: "bg-semantic-warning-light text-semantic-warning",
  error: "bg-semantic-error-light text-semantic-error",
  info: "bg-semantic-info-light text-semantic-info",
  neutral: "bg-surface-muted text-ink-secondary",
  brand: "bg-brand-primary-light text-brand-primary",
};

export default function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-pill px-2.5 py-1 text-caption font-medium ${TONE_CLASSES[tone]}`}
    >
      {children}
    </span>
  );
}
