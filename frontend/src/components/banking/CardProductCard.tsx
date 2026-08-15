import { Link } from "react-router-dom";
import type { CardProduct } from "@/data/cards";
import Button from "@/components/common/Button";
import { formatINR } from "@/utils/format";

import platinumImg from "@/assets/cards/banksphere-platinum.svg";
import cashbackImg from "@/assets/cards/banksphere-cashback.svg";
import travelImg from "@/assets/cards/banksphere-travel.svg";
import rewardsImg from "@/assets/cards/banksphere-rewards.svg";
import debitImg from "@/assets/cards/banksphere-debit.svg";

const CARD_IMAGES: Record<CardProduct["theme"], string> = {
  platinum: platinumImg,
  cashback: cashbackImg,
  travel: travelImg,
  rewards: rewardsImg,
  debit: debitImg,
};

export default function CardProductCard({ card }: { card: CardProduct }) {
  return (
    <div className="flex flex-col bg-white rounded-lg border border-surface-border overflow-hidden transition-all duration-200 hover:-translate-y-1 hover:rotate-[0.4deg] hover:shadow-elevation-3">
      <div className="bg-surface-muted p-6 flex justify-center">
        <img src={CARD_IMAGES[card.theme]} alt={`${card.name} card design`} className="w-full max-w-[280px] h-auto" />
      </div>
      <div className="p-6 flex flex-col flex-1">
        <h3 className="text-h3 text-ink-primary">{card.name}</h3>
        <p className="mt-1 text-body-sm text-ink-secondary">{card.tagline}</p>

        <div className="mt-4 flex items-baseline gap-1.5">
          <span className="text-label text-ink-muted">Annual Fee</span>
        </div>
        <p className="text-h2 text-ink-primary">{card.annualFee === 0 ? "Free" : formatINR(card.annualFee)}</p>

        <ul className="mt-4 space-y-1.5 flex-1">
          {card.benefits.slice(0, 3).map((benefit) => (
            <li key={benefit} className="flex items-start gap-2 text-body-sm text-ink-secondary">
              <span className="mt-1.5 w-1 h-1 rounded-full bg-brand-accent shrink-0" />
              {benefit}
            </li>
          ))}
        </ul>

        <Link to={`/cards/${card.slug}`} className="mt-5">
          <Button variant="outline" fullWidth>
            Explore Card
          </Button>
        </Link>
      </div>
    </div>
  );
}
