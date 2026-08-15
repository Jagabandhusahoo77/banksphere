import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import Button from "@/components/common/Button";
import Modal from "@/components/common/Modal";
import Icon from "@/components/common/Icon";
import FAQAccordion from "@/components/common/FAQAccordion";
import { FAQS } from "@/data/faqs";

interface KeyFact {
  label: string;
  value: string;
}

// Tailwind's JIT scanner needs complete literal class names in source —
// `grid-cols-${n}` would never be generated, so key-fact counts map to a
// fixed set of known-safe classes instead.
const KEY_FACT_GRID_CLASSES: Record<number, string> = {
  1: "grid-cols-1",
  2: "sm:grid-cols-2",
  3: "sm:grid-cols-3",
};

interface ProductDetailLayoutProps {
  eyebrow: string;
  title: string;
  description: string;
  image: string;
  keyFacts: KeyFact[];
  benefits: string[];
  eligibility: string[];
  documents: string[];
  ctaLabel: string;
  disclaimer?: string;
  extra?: ReactNode;
}

export default function ProductDetailLayout({
  eyebrow,
  title,
  description,
  image,
  keyFacts,
  benefits,
  eligibility,
  documents,
  ctaLabel,
  disclaimer,
  extra,
}: ProductDetailLayoutProps) {
  const [applyOpen, setApplyOpen] = useState(false);

  return (
    <div>
      {/* Hero */}
      <section className="bg-brand-primary-light">
        <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm grid grid-cols-1 md:grid-cols-2 gap-10 items-center">
          <div>
            <p className="text-label text-brand-primary uppercase tracking-wide">{eyebrow}</p>
            <h1 className="mt-2 text-h1 text-ink-primary">{title}</h1>
            <p className="mt-4 text-body text-ink-secondary max-w-lg">{description}</p>
            <Button variant="primary" size="lg" className="mt-7" onClick={() => setApplyOpen(true)}>
              {ctaLabel}
            </Button>
            {disclaimer && <p className="mt-4 text-caption text-ink-muted max-w-md">{disclaimer}</p>}
          </div>
          <div className="flex justify-center">
            <img src={image} alt="" className="w-full max-w-sm" />
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm space-y-14">
        {/* Rates / key facts */}
        <section>
          <div className={`grid gap-5 ${KEY_FACT_GRID_CLASSES[Math.min(keyFacts.length, 3)] ?? "grid-cols-1"}`}>
            {keyFacts.map((fact) => (
              <div key={fact.label} className="bg-white rounded-lg border border-surface-border p-5">
                <p className="text-label text-ink-muted">{fact.label}</p>
                <p className="mt-1 text-h2 text-ink-primary">{fact.value}</p>
              </div>
            ))}
          </div>
        </section>

        {/* Benefits */}
        <section>
          <h2 className="text-h2 text-ink-primary mb-5">Key benefits</h2>
          <ul className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {benefits.map((benefit) => (
              <li key={benefit} className="flex items-start gap-3 bg-white rounded-lg border border-surface-border p-4">
                <span className="flex items-center justify-center w-7 h-7 rounded-full bg-semantic-success-light text-semantic-success shrink-0">
                  <Icon name="check" size={14} />
                </span>
                <span className="text-body-sm text-ink-secondary">{benefit}</span>
              </li>
            ))}
          </ul>
        </section>

        {extra}

        {/* Eligibility & documents */}
        <section className="grid grid-cols-1 sm:grid-cols-2 gap-8">
          <div>
            <h2 className="text-h3 text-ink-primary mb-4">Eligibility</h2>
            <ul className="space-y-2">
              {eligibility.map((item) => (
                <li key={item} className="flex items-start gap-2 text-body-sm text-ink-secondary">
                  <span className="mt-2 w-1.5 h-1.5 rounded-full bg-brand-primary shrink-0" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
          {documents.length > 0 && (
            <div>
              <h2 className="text-h3 text-ink-primary mb-4">Documents required</h2>
              <ul className="space-y-2">
                {documents.map((item) => (
                  <li key={item} className="flex items-start gap-2 text-body-sm text-ink-secondary">
                    <span className="mt-2 w-1.5 h-1.5 rounded-full bg-brand-primary shrink-0" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>

        {/* FAQ */}
        <section>
          <h2 className="text-h2 text-ink-primary mb-5">Frequently asked questions</h2>
          <FAQAccordion items={FAQS} />
        </section>

        {/* Final CTA */}
        <section className="text-center bg-surface-muted rounded-xl p-8">
          <h2 className="text-h2 text-ink-primary">Ready to get started?</h2>
          <p className="mt-2 text-body-sm text-ink-secondary">Our team can walk you through the next steps.</p>
          <div className="mt-5 flex flex-col sm:flex-row gap-3 justify-center">
            <Button variant="primary" onClick={() => setApplyOpen(true)}>
              {ctaLabel}
            </Button>
            <Link to="/contact">
              <Button variant="outline">Talk to us</Button>
            </Link>
          </div>
        </section>
      </div>

      <Modal open={applyOpen} onClose={() => setApplyOpen(false)} title="Applications aren't open yet">
        <p className="text-body-sm text-ink-secondary">
          Online applications for {title} aren't available in this phase of BankSphere — this is a portfolio
          demonstration project, not a live bank. In a full deployment, this is where the application flow
          would begin.
        </p>
        <div className="mt-6 flex flex-col sm:flex-row gap-3">
          <Link to="/contact" className="flex-1" onClick={() => setApplyOpen(false)}>
            <Button variant="primary" fullWidth>
              Contact us instead
            </Button>
          </Link>
          <Button variant="outline" fullWidth onClick={() => setApplyOpen(false)}>
            Close
          </Button>
        </div>
      </Modal>
    </div>
  );
}
