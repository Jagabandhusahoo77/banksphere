import { Link } from "react-router-dom";
import Button from "@/components/common/Button";

interface PromotionBannerProps {
  eyebrow?: string;
  title: string;
  description: string;
  ctaLabel: string;
  ctaTo: string;
  image: string;
  imageAlt?: string;
  reverse?: boolean;
  /**
   * A Tailwind `object-*` position class (e.g. "object-right-top") for
   * photographs that have their own baked-in text/graphics off-center —
   * lets a section crop toward the actual photographic subject instead of
   * a text panel. Defaults to centered, matching every prior (SVG)
   * campaign image, which has no off-center content to avoid.
   */
  imagePosition?: string;
}

/** A single BankSphere promotional campaign — fictional/demo, never a copy of a real bank's advertisement. */
export default function PromotionBanner({
  eyebrow,
  title,
  description,
  ctaLabel,
  ctaTo,
  image,
  imageAlt = "",
  reverse = false,
  imagePosition = "object-center",
}: PromotionBannerProps) {
  return (
    <div
      className={`grid grid-cols-1 md:grid-cols-2 gap-8 items-center bg-white rounded-xl border border-surface-border overflow-hidden ${
        reverse ? "md:[&>*:first-child]:order-2" : ""
      }`}
    >
      <div className="p-6 sm:p-8">
        {eyebrow && <p className="text-label text-brand-primary uppercase tracking-wide mb-2">{eyebrow}</p>}
        <h3 className="text-h2 text-ink-primary">{title}</h3>
        <p className="mt-3 text-body text-ink-secondary">{description}</p>
        <Link to={ctaTo} className="inline-block mt-6">
          <Button variant="primary">{ctaLabel}</Button>
        </Link>
      </div>
      <img
        src={image}
        alt={imageAlt}
        loading="lazy"
        className={`w-full h-full object-cover max-h-64 md:max-h-full ${imagePosition}`}
      />
    </div>
  );
}
