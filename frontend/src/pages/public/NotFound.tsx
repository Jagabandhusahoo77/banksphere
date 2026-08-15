import { Link } from "react-router-dom";
import Button from "@/components/common/Button";
import emptyStateIllustration from "@/assets/images/empty-state.svg";

export default function NotFound() {
  return (
    <div className="min-h-[70vh] flex flex-col items-center justify-center text-center px-4 py-16">
      <img src={emptyStateIllustration} alt="" className="w-40 h-auto mb-6" />
      <p className="text-label text-brand-primary uppercase tracking-wide">404</p>
      <h1 className="mt-2 text-h1 text-ink-primary">We couldn't find that page</h1>
      <p className="mt-3 text-body text-ink-secondary max-w-md">
        The page you're looking for may have moved or doesn't exist. Let's get you back to
        somewhere useful.
      </p>
      <div className="mt-8 flex flex-col sm:flex-row gap-3">
        <Link to="/">
          <Button variant="primary">Go to homepage</Button>
        </Link>
        <Link to="/dashboard">
          <Button variant="outline">Go to dashboard</Button>
        </Link>
      </div>
    </div>
  );
}
