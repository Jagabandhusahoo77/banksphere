import Icon, { type IconName } from "@/components/common/Icon";

const VALUES: { icon: IconName; title: string; description: string }[] = [
  {
    icon: "shield-check",
    title: "Security first",
    description: "Every feature is designed with the assumption that it's protecting someone's money.",
  },
  {
    icon: "eye",
    title: "Transparency",
    description: "Clear balances, clear transaction history, no fine print surprises.",
  },
  {
    icon: "trending-up",
    title: "Built for growth",
    description: "From a first savings account to long-term investing, in one connected experience.",
  },
  {
    icon: "headset",
    title: "People behind the product",
    description: "Digital-first doesn't mean help is ever far away.",
  },
];

export default function About() {
  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <div className="max-w-2xl">
        <p className="text-label text-brand-primary uppercase tracking-wide">About BankSphere</p>
        <h1 className="mt-2 text-h1 text-ink-primary">A digital bank, built from first principles</h1>
        <p className="mt-4 text-body text-ink-secondary">
          BankSphere is a fictional digital banking platform created as a demonstration project. It
          isn't a real bank, isn't affiliated with any real financial institution, and doesn't hold
          real money — but every screen is built the way a real banking product would be: careful
          about correctness, careful about security, and straightforward about what it can and
          can't do yet.
        </p>
      </div>

      <div className="mt-14 grid grid-cols-1 sm:grid-cols-2 gap-8">
        {VALUES.map((value) => (
          <div key={value.title} className="flex items-start gap-4">
            <span className="flex items-center justify-center w-11 h-11 rounded-full bg-brand-primary-light text-brand-primary shrink-0">
              <Icon name={value.icon} size={20} />
            </span>
            <div>
              <p className="text-h3 text-ink-primary">{value.title}</p>
              <p className="mt-1 text-body-sm text-ink-secondary">{value.description}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-14 rounded-lg border border-surface-border bg-surface-muted p-6">
        <p className="text-body-sm text-ink-secondary">
          <strong className="text-ink-primary">A note on scope:</strong> BankSphere is being built in
          phases. Today, that means real account creation, deposits, withdrawals and transaction
          history running against a real backend — with sign-in still a simplified placeholder ahead
          of a dedicated authentication phase. Areas like cards, loans, payments and investments are
          visible in the navigation so the product's direction is clear, but are intentionally marked
          "coming soon" rather than simulated.
        </p>
      </div>
    </div>
  );
}
