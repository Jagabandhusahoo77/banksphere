import { Link } from "react-router-dom";
import Button from "./Button";

interface ComingSoonPageProps {
  title: string;
  description: string;
  illustration: string;
  plannedFeatures: string[];
}

/**
 * Shared shell for feature areas that have a route and navigation entry
 * but no backend support yet (Cards, Loans, Payments, Investments,
 * Profile, Support). Renders an honest "coming soon" state — never fakes
 * data or a working form for functionality the backend doesn't have.
 */
export default function ComingSoonPage({ title, description, illustration, plannedFeatures }: ComingSoonPageProps) {
  return (
    <div className="max-w-2xl mx-auto text-center py-8">
      <img src={illustration} alt="" className="w-48 h-auto mx-auto mb-6" />
      <h1 className="text-h1 text-ink-primary">{title}</h1>
      <p className="mt-3 text-body text-ink-secondary">{description}</p>

      <div className="mt-8 bg-white border border-surface-border rounded-lg p-6 text-left">
        <p className="text-label text-ink-muted mb-3">What's planned</p>
        <ul className="space-y-2">
          {plannedFeatures.map((feature) => (
            <li key={feature} className="flex items-start gap-2 text-body-sm text-ink-secondary">
              <span className="mt-2 w-1.5 h-1.5 rounded-full bg-brand-accent shrink-0" />
              {feature}
            </li>
          ))}
        </ul>
      </div>

      <div className="mt-8 flex flex-col sm:flex-row gap-3 justify-center">
        <Link to="/dashboard">
          <Button variant="primary">Back to dashboard</Button>
        </Link>
        <Link to="/support">
          <Button variant="outline">Contact support</Button>
        </Link>
      </div>
    </div>
  );
}
