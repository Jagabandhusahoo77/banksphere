import { Link } from "react-router-dom";
import FAQAccordion from "@/components/common/FAQAccordion";
import Button from "@/components/common/Button";
import Icon from "@/components/common/Icon";
import { FAQS } from "@/data/faqs";

/**
 * A real public help page — not a ComingSoonPage. Recomposes the existing
 * FAQAccordion/FAQS content (already real, already used on product detail
 * pages) rather than fabricating new support content; the chatbot is
 * available globally on every page for anything not answered here.
 */
export default function Help() {
  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <div className="max-w-2xl">
        <p className="text-label text-brand-primary uppercase tracking-wide">Help &amp; Support</p>
        <h1 className="mt-2 text-h1 text-ink-primary">How can we help?</h1>
        <p className="mt-4 text-body text-ink-secondary">
          Answers to common questions about BankSphere accounts, cards, loans, and deposits. For anything else, our
          chat assistant is available in the corner of every page, or you can reach out directly.
        </p>
      </div>

      <div className="mt-10 max-w-2xl">
        <FAQAccordion items={FAQS} />
      </div>

      <div className="mt-10 flex flex-col sm:flex-row gap-3">
        <Link to="/contact">
          <Button variant="primary" icon="mail" iconPosition="left">
            Contact us
          </Button>
        </Link>
        <span className="inline-flex items-center gap-1.5 text-body-sm text-ink-secondary">
          <Icon name="headset" size={18} />
          Or use the chat assistant in the corner of this page
        </span>
      </div>
    </div>
  );
}
