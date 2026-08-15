import { useParams } from "react-router-dom";
import { getCardBySlug, type CardTheme } from "@/data/cards";
import ProductDetailLayout from "@/components/banking/ProductDetailLayout";
import NotFound from "@/pages/public/NotFound";
import { formatINR } from "@/utils/format";

import platinumImg from "@/assets/cards/banksphere-platinum.svg";
import cashbackImg from "@/assets/cards/banksphere-cashback.svg";
import travelImg from "@/assets/cards/banksphere-travel.svg";
import rewardsImg from "@/assets/cards/banksphere-rewards.svg";
import debitImg from "@/assets/cards/banksphere-debit.svg";

const CARD_IMAGES: Record<CardTheme, string> = {
  platinum: platinumImg,
  cashback: cashbackImg,
  travel: travelImg,
  rewards: rewardsImg,
  debit: debitImg,
};

export default function CardDetail() {
  const { slug } = useParams<{ slug: string }>();
  const card = slug ? getCardBySlug(slug) : undefined;

  if (!card) {
    return <NotFound />;
  }

  return (
    <ProductDetailLayout
      eyebrow="BankSphere Cards"
      title={card.name}
      description={card.tagline}
      image={CARD_IMAGES[card.theme]}
      keyFacts={[
        { label: "Annual fee", value: card.annualFee === 0 ? "Free" : formatINR(card.annualFee) },
        { label: "Network", value: card.network },
        ...(card.annualFeeWaiver ? [{ label: "Fee waiver", value: card.annualFeeWaiver }] : []),
      ]}
      benefits={card.benefits}
      eligibility={card.eligibility}
      documents={card.documents}
      ctaLabel="Apply for this card"
      disclaimer="Demo product — for portfolio demonstration only. Fees and benefits shown are fictional."
    />
  );
}
