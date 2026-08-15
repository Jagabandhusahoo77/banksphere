import { CARD_PRODUCTS } from "@/data/cards";
import CardProductCard from "@/components/banking/CardProductCard";
import ProductComparison from "@/components/banking/ProductComparison";

const CREDIT_CARDS = CARD_PRODUCTS.filter((card) => card.slug !== "debit");

export default function CardsCatalog() {
  return (
    <div>
      <section className="bg-brand-primary-light">
        <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm text-center">
          <p className="text-label text-brand-primary uppercase tracking-wide">Cards</p>
          <h1 className="mt-2 text-h1 text-ink-primary">Find the right card for you</h1>
          <p className="mt-3 text-body text-ink-secondary max-w-xl mx-auto">
            From everyday cashback to premium travel benefits, BankSphere cards are built around how you actually
            spend.
          </p>
        </div>
      </section>

      <section className="mx-auto max-w-content px-4 sm:px-6 py-section-sm">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {CREDIT_CARDS.map((card) => (
            <CardProductCard key={card.slug} card={card} />
          ))}
        </div>
        <p className="mt-6 text-caption text-ink-muted text-center">
          Demo products — for portfolio demonstration only. Fees and benefits shown are fictional.
        </p>
      </section>

      <section className="mx-auto max-w-content px-4 sm:px-6 py-section-sm">
        <h2 className="text-h2 text-ink-primary mb-6">Compare cards</h2>
        <ProductComparison
          columns={CREDIT_CARDS.map((card) => card.name.replace("BankSphere ", ""))}
          rows={[
            { feature: "Annual fee", values: CREDIT_CARDS.map((card) => (card.annualFee === 0 ? "Free" : `₹${card.annualFee.toLocaleString("en-IN")}`)) },
            { feature: "Network", values: CREDIT_CARDS.map((card) => card.network) },
            { feature: "Key benefit", values: CREDIT_CARDS.map((card) => card.benefits[0]) },
          ]}
        />
      </section>
    </div>
  );
}
