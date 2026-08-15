import type { Offer } from "@/data/offers";
import Badge from "@/components/common/Badge";

export default function OfferCard({ offer }: { offer: Offer }) {
  return (
    <div className="bg-white rounded-lg border border-surface-border p-5 flex flex-col gap-2">
      <Badge tone="brand">{offer.category}</Badge>
      <p className="text-h3 text-ink-primary">{offer.title}</p>
      <p className="text-body-sm text-ink-secondary">{offer.description}</p>
      <p className="text-caption text-ink-muted mt-1">BankSphere demo offer — for portfolio demonstration only.</p>
    </div>
  );
}
