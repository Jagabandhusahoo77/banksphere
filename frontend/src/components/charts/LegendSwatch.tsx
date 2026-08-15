/** A single legend entry — a small rect swatch (bars/areas use a rect key, lines use a stroke key) plus a text-token label, never a colored label. */
export default function LegendSwatch({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-body-sm text-ink-secondary">
      <span className="w-3 h-3 rounded-sm shrink-0" style={{ backgroundColor: color }} aria-hidden="true" />
      {label}
    </span>
  );
}
