import Icon from "./Icon";

export default function Spinner({ size = 20, label = "Loading" }: { size?: number; label?: string }) {
  return (
    <span role="status" className="inline-flex items-center gap-2 text-ink-secondary">
      <Icon name="spinner" size={size} className="animate-spin" />
      <span className="sr-only">{label}</span>
    </span>
  );
}
