import { useState } from "react";
import type { Insight } from "@/data/insights";
import Badge from "@/components/common/Badge";
import Button from "@/components/common/Button";
import Modal from "@/components/common/Modal";
import Icon from "@/components/common/Icon";

export default function InsightCard({ insight }: { insight: Insight }) {
  const [open, setOpen] = useState(false);

  return (
    <div className="flex flex-col bg-white rounded-lg border border-surface-border overflow-hidden transition-all duration-200 hover:-translate-y-1 hover:shadow-elevation-3">
      <img src={insight.image} alt="" className="w-full h-36 object-cover" />
      <div className="p-5 flex flex-col flex-1">
        <Badge tone="brand">{insight.category}</Badge>
        <h3 className="mt-3 text-h3 text-ink-primary">{insight.title}</h3>
        <p className="mt-2 text-body-sm text-ink-secondary flex-1">{insight.summary}</p>
        <div className="mt-4 flex items-center gap-3 text-caption text-ink-muted">
          <span className="flex items-center gap-1">
            <Icon name="calendar" size={14} />
            {insight.date}
          </span>
          <span className="flex items-center gap-1">
            <Icon name="clock" size={14} />
            {insight.readingTimeMinutes} min read
          </span>
        </div>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="mt-4 inline-flex items-center gap-1.5 self-start text-body-sm font-medium text-brand-primary hover:underline"
        >
          Read article
          <Icon name="arrow-right" size={16} />
        </button>
      </div>

      <Modal open={open} onClose={() => setOpen(false)} title="Full articles aren't published yet">
        <p className="text-body-sm text-ink-secondary">
          BankSphere's Insights section is a portfolio demonstration — this article summary is real, but the full
          article isn't published anywhere yet. In a full deployment, this is where the complete piece would open.
        </p>
        <Button variant="outline" fullWidth className="mt-6" onClick={() => setOpen(false)}>
          Close
        </Button>
      </Modal>
    </div>
  );
}
