import { useState } from "react";
import type { Faq } from "@/data/faqs";
import Icon from "./Icon";

export default function FAQAccordion({ items }: { items: Faq[] }) {
  const [openIds, setOpenIds] = useState<Set<string>>(new Set());

  const toggle = (id: string) => {
    setOpenIds((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  return (
    <div className="divide-y divide-surface-border border-y border-surface-border">
      {items.map((item) => {
        const isOpen = openIds.has(item.id);
        const panelId = `faq-panel-${item.id}`;
        const buttonId = `faq-button-${item.id}`;

        return (
          <div key={item.id}>
            <h3>
              <button
                type="button"
                id={buttonId}
                aria-expanded={isOpen}
                aria-controls={panelId}
                onClick={() => toggle(item.id)}
                className="w-full flex items-center justify-between gap-4 py-4 text-left"
              >
                <span className="text-body font-medium text-ink-primary">{item.question}</span>
                <Icon
                  name="chevron-down"
                  size={18}
                  className={`shrink-0 text-ink-muted transition-transform ${isOpen ? "rotate-180" : ""}`}
                />
              </button>
            </h3>
            <div id={panelId} role="region" aria-labelledby={buttonId} hidden={!isOpen} className="pb-4">
              <p className="text-body-sm text-ink-secondary">{item.answer}</p>
            </div>
          </div>
        );
      })}
    </div>
  );
}
