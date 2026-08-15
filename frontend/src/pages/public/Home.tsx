import { useEffect, type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import Button from "@/components/common/Button";
import Icon, { type IconName } from "@/components/common/Icon";
import ServiceCard from "@/components/banking/ServiceCard";
import SecurityBanner from "@/components/banking/SecurityBanner";
import CardProductCard from "@/components/banking/CardProductCard";
import LoanProductCard from "@/components/banking/LoanProductCard";
import LoanCalculator from "@/components/banking/LoanCalculator";
import FixedDepositCalculator from "@/components/banking/FixedDepositCalculator";
import PromotionBanner from "@/components/banking/PromotionBanner";
import OfferCard from "@/components/banking/OfferCard";
import InsightCard from "@/components/banking/InsightCard";
import FAQAccordion from "@/components/common/FAQAccordion";
import { useInViewport } from "@/hooks/useInViewport";

import { CARD_PRODUCTS } from "@/data/cards";
import { LOAN_PRODUCTS, getLoanBySlug } from "@/data/loans";
import { DEPOSIT_PRODUCTS } from "@/data/deposits";
import { OFFERS } from "@/data/offers";
import { INSIGHTS } from "@/data/insights";
import { FAQS } from "@/data/faqs";
import { QUICK_CATEGORIES } from "@/data/navigation";

import savingsIcon from "@/assets/icons/savings.svg";
import cardIcon from "@/assets/icons/card.svg";
import personalLoanIcon from "@/assets/icons/personal-loan.svg";
import homeLoanIcon from "@/assets/icons/home-loan.svg";
import investmentsIcon from "@/assets/icons/investments.svg";
import upiIcon from "@/assets/icons/upi.svg";
import mobileBankingIcon from "@/assets/icons/mobile-banking.svg";
import billPaymentIcon from "@/assets/icons/bill-payment.svg";
import statementIcon from "@/assets/icons/statement.svg";
import serviceRequestIcon from "@/assets/icons/service-request.svg";

import goDigitalPromo from "@/assets/promotions/go-digital.svg";
import homeFinancingPromo from "@/assets/promotions/home-financing.svg";
import growSavingsPromo from "@/assets/promotions/grow-savings.svg";

// Real photography (Phase 3C photography correction) — see
// docs/frontend/homepage-design.md#photography for the full mapping and
// the crop/object-position treatment applied to each. SVG illustrations
// they replace (hero-lifestyle.svg, goals-family.svg,
// mobile-banking-scene.svg, accounts-overview.svg) are kept on disk,
// unused here, per explicit instruction not to delete them — they may
// still suit smaller/decorative contexts elsewhere.
import familyBankingPhoto from "@/assets/images/hero/family-banking.webp";
import savingsPhoto from "@/assets/images/deposits/savings-fixed-deposit.webp";
import creditCardsPhoto from "@/assets/images/cards/credit-cards.webp";
import mobileBankingPhoto from "@/assets/images/mobile/mobile-banking.webp";
import travelRewardsPhoto from "@/assets/images/promotions/travel-rewards.webp";
import digitalBankingPhoto from "@/assets/images/promotions/digital-banking.webp";
import financialGoalsPhoto from "@/assets/images/family/financial-goals.webp";

const POPULAR_PRODUCTS = [
  {
    iconSrc: savingsIcon,
    title: "Savings Account",
    description: "A digital-first account for everyday banking, with no minimum balance.",
    benefit: "Up to 4.00% p.a.",
    to: "/savings-account",
  },
  {
    iconSrc: cardIcon,
    title: "Credit Cards",
    description: "Flexible spending with rewards designed around your habits.",
    benefit: "Rewards on every purchase",
    to: "/cards",
  },
  {
    iconSrc: homeLoanIcon,
    title: "Home Loan",
    description: "Competitive rates to help you get to your front door.",
    benefit: "Starting at 8.50% p.a.",
    to: "/loans/home",
  },
  {
    iconSrc: personalLoanIcon,
    title: "Personal Loan",
    description: "Quick approvals for the moments that can't wait.",
    benefit: "No collateral required",
    to: "/loans/personal",
  },
  {
    iconSrc: investmentsIcon,
    title: "Fixed Deposit",
    description: "Lock in a rate and watch your savings compound.",
    benefit: "Up to 7.25% p.a.",
    to: "/deposits/fixed",
  },
];

const DIGITAL_BANKING_SERVICES: { icon: string; title: string; description: string; to: string }[] = [
  { icon: upiIcon, title: "UPI Payments", description: "Instant payments to anyone, anytime.", to: "/payments" },
  { icon: mobileBankingIcon, title: "Money Transfer", description: "Move funds between accounts in seconds.", to: "/transfer" },
  { icon: billPaymentIcon, title: "Bill Payments", description: "Pay bills on schedule, without the paperwork.", to: "/payments" },
  { icon: statementIcon, title: "Account Statements", description: "Download and review your account history.", to: "/transactions" },
  { icon: serviceRequestIcon, title: "Service Requests", description: "Raise and track requests without visiting a branch.", to: "/support" },
  { icon: cardIcon, title: "Card Controls", description: "Freeze, unfreeze, or set limits on your cards instantly.", to: "/banking/cards" },
];

const PROMOTIONS = [
  {
    eyebrow: "Digital banking",
    title: "Go digital. Get rewarded.",
    description: "Open your BankSphere account online and enjoy introductory digital banking benefits.",
    ctaLabel: "Open an Account",
    ctaTo: "/contact",
    image: goDigitalPromo,
  },
  {
    eyebrow: "Cards",
    title: "Make your next journey more rewarding.",
    description: "Explore BankSphere travel and rewards cards, built for the way you move.",
    ctaLabel: "Explore Cards",
    ctaTo: "/cards",
    image: travelRewardsPhoto,
    imageAlt: "A couple travelling together, holding BankSphere travel rewards cards",
    imagePosition: "object-right-top",
    reverse: true,
  },
  {
    eyebrow: "Home loans",
    title: "Plan today. Own tomorrow.",
    description: "Explore home financing options designed around your goals.",
    ctaLabel: "Explore Home Loans",
    ctaTo: "/loans/home",
    image: homeFinancingPromo,
  },
  {
    eyebrow: "Deposits",
    title: "Grow your savings.",
    description: "Explore BankSphere Fixed Deposits and watch your savings compound.",
    ctaLabel: "Explore Deposits",
    ctaTo: "/deposits",
    image: growSavingsPromo,
    reverse: true,
  },
];

const TRUST_PRINCIPLES: { icon: IconName; title: string; description: string }[] = [
  { icon: "shield-check", title: "Secure by Design", description: "Security is built into every session and transaction from the start, not added on afterward." },
  { icon: "clock", title: "24/7 Digital Access", description: "Your accounts and support are available whenever you need them, day or night." },
  { icon: "eye", title: "Transparent Banking", description: "Clear balances, clear transaction history, and rates that are always labeled honestly." },
  { icon: "headset", title: "Customer First", description: "Support whenever customers need help, including a live chat assistant." },
];

const FEATURED_CARDS = CARD_PRODUCTS.filter((card) => card.slug !== "debit").slice(0, 3);
const HOME_LOAN = getLoanBySlug("home");

function parseStartingRate(rateLabel: string): number {
  const match = rateLabel.match(/[\d.]+/);
  return match ? Number(match[0]) : 8.5;
}

/** One-time fade/slide-in when a section first scrolls into view — see useInViewport's reduced-motion handling. */
function Reveal({ children }: { children: ReactNode }) {
  const { ref, isVisible } = useInViewport<HTMLDivElement>({ threshold: 0.12 });
  return (
    <div
      ref={ref}
      className={`transition-all duration-700 ease-out ${isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-6"}`}
    >
      {children}
    </div>
  );
}

export default function Home() {
  const location = useLocation();

  // React Router's <Link> (used by PromotionBanner/QuickCategory CTAs that
  // point at in-page anchors like "/#digital-banking-services") does
  // client-side navigation and doesn't trigger the browser's native
  // same-document hash-scroll — this handles it manually, on mount and on
  // every hash change, covering both "already on /" and "navigated here
  // from another page" cases uniformly.
  useEffect(() => {
    if (!location.hash) return;
    const target = document.querySelector(location.hash);
    target?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [location.hash]);

  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-br from-brand-primary-light via-white to-brand-accent-light">
        <div className="absolute -top-24 -right-24 w-96 h-96 rounded-full bg-brand-secondary/20 blur-3xl" aria-hidden="true" />
        <div className="absolute -bottom-32 -left-16 w-80 h-80 rounded-full bg-brand-accent/20 blur-3xl" aria-hidden="true" />
        <div className="relative mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
          <div>
            <h1 className="text-h1 lg:text-display text-ink-primary">Banking that fits your life goals.</h1>
            <p className="mt-5 text-body lg:text-h3 lg:font-normal text-ink-secondary max-w-lg">
              From everyday banking to long-term wealth creation, we're with you at every step of your financial
              journey.
            </p>
            <div className="mt-8 flex flex-col sm:flex-row gap-3">
              <Link to="/contact">
                <Button variant="primary" size="lg" fullWidth icon="arrow-right" iconPosition="right">
                  Open an Account
                </Button>
              </Link>
              <Link to="/#popular-products">
                <Button variant="outline" size="lg" fullWidth>
                  Explore Products
                </Button>
              </Link>
            </div>
            <div className="mt-8 flex flex-wrap gap-x-6 gap-y-2">
              {["Secure Banking", "24/7 Digital Access", "Trusted Service"].map((trust) => (
                <span key={trust} className="flex items-center gap-1.5 text-body-sm text-ink-secondary">
                  <Icon name="check" size={16} className="text-semantic-success" />
                  {trust}
                </span>
              ))}
            </div>
          </div>
          <div className="flex justify-center">
            <div className="w-full max-w-md aspect-[4/5] rounded-2xl overflow-hidden shadow-elevation-3">
              <img
                src={familyBankingPhoto}
                alt="A family sitting together at home, looking at a tablet while managing their BankSphere account"
                fetchPriority="high"
                className="w-full h-full object-cover object-right"
              />
            </div>
          </div>
        </div>
      </section>

      {/* Quick Banking Categories */}
      <section className="border-b border-surface-border bg-white">
        <div className="mx-auto max-w-content px-4 sm:px-6 py-6">
          <div className="flex gap-4 overflow-x-auto sm:grid sm:grid-cols-4 lg:grid-cols-7 sm:overflow-visible pb-1">
            {QUICK_CATEGORIES.map((category) => (
              <Link
                key={category.label}
                to={category.to}
                className="group flex flex-col items-center text-center gap-2 shrink-0 w-24 sm:w-auto rounded-lg p-3 transition-all hover:-translate-y-0.5 hover:bg-surface-muted"
              >
                <span className="flex items-center justify-center w-11 h-11 rounded-full bg-brand-primary-light text-brand-primary group-hover:bg-brand-primary group-hover:text-white transition-colors">
                  <Icon name={category.icon} size={20} />
                </span>
                <span className="text-body-sm font-medium text-ink-primary">{category.label}</span>
                <span className="hidden sm:block text-caption text-ink-muted">{category.description}</span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* Popular products */}
      <Reveal>
        <section id="popular-products" className="scroll-mt-20 py-section-sm lg:py-section bg-gradient-to-b from-surface-background to-white">
          <div className="mx-auto max-w-content px-4 sm:px-6">
            <div className="max-w-2xl">
              <h2 className="text-h1 text-ink-primary">Explore our popular products</h2>
              <p className="mt-3 text-body text-ink-secondary">
                From everyday accounts to long-term investments, BankSphere brings your financial life into one
                place.
              </p>
            </div>
            <div className="mt-10 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-5">
              {POPULAR_PRODUCTS.map((product) => (
                <ServiceCard key={product.title} {...product} />
              ))}
            </div>
          </div>
        </section>
      </Reveal>

      {/* Card showcase */}
      <Reveal>
        <section className="py-section-sm lg:py-section bg-gradient-to-br from-surface-muted via-surface-muted to-brand-accent-light">
          <div className="mx-auto max-w-content px-4 sm:px-6">
            <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-10">
              <div className="max-w-2xl">
                <h2 className="text-h1 text-ink-primary">Find the right card for you</h2>
                <p className="mt-3 text-body text-ink-secondary">
                  Premium rewards, everyday cashback, or travel benefits — original BankSphere cards.
                </p>
              </div>
              <Link to="/cards" className="shrink-0">
                <Button variant="outline">View all cards</Button>
              </Link>
            </div>
            <div className="mb-8 rounded-2xl overflow-hidden shadow-elevation-2">
              <img
                src={creditCardsPhoto}
                alt="BankSphere Infinite, Platinum, and Classic credit cards displayed together"
                loading="lazy"
                className="w-full h-56 sm:h-72 object-cover object-right"
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
              {FEATURED_CARDS.map((card) => (
                <CardProductCard key={card.slug} card={card} />
              ))}
            </div>
          </div>
        </section>
      </Reveal>

      {/* Mobile / Digital Banking promotion */}
      <Reveal>
        <section className="py-section-sm lg:py-section">
          <div className="mx-auto max-w-content px-4 sm:px-6">
            <PromotionBanner
              eyebrow="Digital banking"
              title="Bank on the go with BankSphere Mobile"
              description="Check balances, move money, and manage your cards from wherever you are — a full banking experience designed for your phone."
              ctaLabel="Explore Mobile Banking"
              ctaTo="/#digital-banking-services"
              image={mobileBankingPhoto}
              imageAlt="A woman checking her BankSphere account balance and quick actions on her phone"
              imagePosition="object-right-top"
            />
          </div>
        </section>
      </Reveal>

      {/* Loans */}
      <Reveal>
        <section className="py-section-sm lg:py-section bg-gradient-to-b from-brand-primary-light to-surface-muted">
          <div className="mx-auto max-w-content px-4 sm:px-6">
            <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-10">
              <div className="max-w-2xl">
                <h2 className="text-h1 text-ink-primary">Loans designed around your goals</h2>
                <p className="mt-3 text-body text-ink-secondary">
                  Illustrative BankSphere demo rates — competitive starting rates across home, personal, car and
                  education financing.
                </p>
              </div>
              <Link to="/loans" className="shrink-0">
                <Button variant="outline">View all loans</Button>
              </Link>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
              {LOAN_PRODUCTS.map((loan) => (
                <LoanProductCard key={loan.slug} loan={loan} />
              ))}
            </div>
            {HOME_LOAN && (
              <div className="mt-10 bg-white rounded-lg border border-surface-border p-6 sm:p-8">
                <h3 className="text-h2 text-ink-primary mb-5">Estimate your EMI</h3>
                <LoanCalculator defaultRate={parseStartingRate(HOME_LOAN.startingRate)} />
              </div>
            )}
          </div>
        </section>
      </Reveal>

      {/* Fixed deposits / savings */}
      <Reveal>
        <section className="py-section-sm lg:py-section bg-gradient-to-b from-white to-brand-secondary/10">
          <div className="mx-auto max-w-content px-4 sm:px-6">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
              <div>
                <h2 className="text-h1 text-ink-primary">Grow your savings with BankSphere Deposits</h2>
                <p className="mt-3 text-body text-ink-secondary max-w-lg">
                  Lock in a rate with a Fixed Deposit, or build a savings habit with a Recurring Deposit.
                </p>
                <div className="mt-6 space-y-3">
                  {DEPOSIT_PRODUCTS.map((deposit) => (
                    <div
                      key={deposit.slug}
                      className="flex items-center justify-between bg-white rounded-lg border border-surface-border shadow-elevation-1 px-5 py-4"
                    >
                      <div>
                        <p className="text-body font-semibold text-ink-primary">{deposit.name}</p>
                        <p className="text-caption text-ink-muted">{deposit.tenure}</p>
                      </div>
                      <p className="text-h3 text-brand-primary">{deposit.rate}*</p>
                    </div>
                  ))}
                </div>
                <p className="mt-3 text-caption text-ink-muted">*Illustrative BankSphere demo rate. Actual rates may vary.</p>
                <div className="mt-6 flex flex-col sm:flex-row gap-3">
                  <Link to="/deposits">
                    <Button variant="primary">Calculate Returns</Button>
                  </Link>
                  <Link to="/deposits">
                    <Button variant="outline">Open a Fixed Deposit</Button>
                  </Link>
                </div>
              </div>
              <div className="flex justify-center">
                <div className="w-full max-w-sm aspect-[4/3] rounded-2xl overflow-hidden shadow-elevation-2">
                  <img
                    src={savingsPhoto}
                    alt="A couple reviewing their BankSphere savings plan together at home"
                    loading="lazy"
                    className="w-full h-full object-cover"
                  />
                </div>
              </div>
            </div>
            <div className="mt-10 bg-surface-muted rounded-lg border border-surface-border p-6 sm:p-8">
              <h3 className="text-h2 text-ink-primary mb-5">Calculate your maturity amount</h3>
              <FixedDepositCalculator />
            </div>
          </div>
        </section>
      </Reveal>

      {/* Exclusive Offers + campaigns */}
      <Reveal>
        <section id="exclusive-offers" className="scroll-mt-20 py-section-sm lg:py-section bg-gradient-to-b from-brand-accent-light to-surface-muted">
          <div className="mx-auto max-w-content px-4 sm:px-6">
            <h2 className="text-h1 text-ink-primary mb-10">Exclusive Offers</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
              {OFFERS.map((offer) => (
                <OfferCard key={offer.id} offer={offer} />
              ))}
            </div>
            <div className="mt-10 space-y-6">
              {PROMOTIONS.map((promo) => (
                <PromotionBanner key={promo.title} {...promo} />
              ))}
            </div>
          </div>
        </section>
      </Reveal>

      {/* Why Choose BankSphere / Trust */}
      <Reveal>
        <section id="security" className="relative overflow-hidden scroll-mt-20 py-section-sm lg:py-section bg-gradient-to-br from-brand-primary-dark via-brand-primary-dark to-brand-primary">
          <div className="absolute top-1/2 -right-20 w-96 h-96 -translate-y-1/2 rounded-full bg-brand-accent/20 blur-3xl" aria-hidden="true" />
          <div className="absolute -bottom-24 -left-24 w-72 h-72 rounded-full bg-brand-secondary/25 blur-3xl" aria-hidden="true" />
          <div className="relative mx-auto max-w-content px-4 sm:px-6">
            <div className="max-w-2xl">
              <h2 className="text-h1 text-white">Why Choose BankSphere</h2>
              <p className="mt-3 text-body text-white/70">
                Every BankSphere session and transaction is designed with these principles as the foundation, not an
                afterthought.
              </p>
            </div>
            <div className="mt-10">
              <SecurityBanner points={TRUST_PRINCIPLES} />
            </div>
          </div>
        </section>
      </Reveal>

      {/* Digital Banking Services */}
      <Reveal>
        <section id="digital-banking-services" className="scroll-mt-20 py-section-sm lg:py-section">
          <div className="mx-auto max-w-content px-4 sm:px-6">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-10 items-center">
              <div className="max-w-2xl">
                <h2 className="text-h1 text-ink-primary">Bank smarter with our digital services</h2>
                <p className="mt-3 text-body text-ink-secondary">
                  A full banking experience designed for your phone, your laptop, and everything in between.
                </p>
              </div>
              <div className="aspect-[4/3] rounded-2xl overflow-hidden shadow-elevation-2">
                <img
                  src={digitalBankingPhoto}
                  alt="A man reviewing his BankSphere account on his phone and laptop at home"
                  loading="lazy"
                  className="w-full h-full object-cover"
                />
              </div>
            </div>
            <div className="mt-10 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
              {DIGITAL_BANKING_SERVICES.map((service) => (
                <Link
                  key={service.title}
                  to={service.to}
                  className="group flex items-start gap-4 rounded-lg p-2 -m-2 transition-colors hover:bg-surface-muted"
                >
                  <img src={service.icon} alt="" className="w-11 h-11 shrink-0" />
                  <div>
                    <p className="text-body font-semibold text-ink-primary">{service.title}</p>
                    <p className="mt-0.5 text-body-sm text-ink-secondary">{service.description}</p>
                  </div>
                </Link>
              ))}
            </div>
          </div>
        </section>
      </Reveal>

      {/* Financial Goals / Lifestyle banner */}
      <Reveal>
        <section className="py-section-sm lg:py-section bg-gradient-to-tr from-brand-primary-light via-brand-primary-light to-brand-accent-light">
          <div className="mx-auto max-w-content px-4 sm:px-6 grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
            <div className="flex justify-center order-2 lg:order-1">
              <div className="w-full max-w-md aspect-[4/5] rounded-2xl overflow-hidden shadow-elevation-3">
                <img
                  src={financialGoalsPhoto}
                  alt="A family celebrating together outside their home after reaching a financial goal with BankSphere"
                  loading="lazy"
                  className="w-full h-full object-cover object-right"
                />
              </div>
            </div>
            <div className="order-1 lg:order-2">
              <h2 className="text-h1 text-ink-primary">
                Your goals.
                <br />
                Our priority.
              </h2>
              <p className="mt-4 text-body text-ink-secondary max-w-md">Let's build a better tomorrow, together.</p>
              <Link to="/contact" className="inline-block mt-6">
                <Button variant="primary" size="lg" icon="arrow-right" iconPosition="right">
                  Open an Account
                </Button>
              </Link>
            </div>
          </div>
        </section>
      </Reveal>

      {/* Latest Insights */}
      <Reveal>
        <section className="py-section-sm lg:py-section">
          <div className="mx-auto max-w-content px-4 sm:px-6">
            <h2 className="text-h1 text-ink-primary mb-10">Latest Updates &amp; Insights</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
              {INSIGHTS.map((insight) => (
                <InsightCard key={insight.id} insight={insight} />
              ))}
            </div>
          </div>
        </section>
      </Reveal>

      {/* FAQ */}
      <section className="py-section-sm lg:py-section bg-surface-muted">
        <div className="mx-auto max-w-3xl px-4 sm:px-6">
          <h2 className="text-h1 text-ink-primary mb-8 text-center">Frequently asked questions</h2>
          <FAQAccordion items={FAQS} />
        </div>
      </section>
    </>
  );
}
