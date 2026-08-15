import { Link } from "react-router-dom";
import Icon from "@/components/common/Icon";
import Button from "@/components/common/Button";

export default function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-background px-4">
      <div className="text-center max-w-sm">
        <span className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-surface-muted text-ink-secondary mb-5">
          <Icon name="alert-circle" size={28} />
        </span>
        <h1 className="text-h2 text-ink-primary mb-2">Page not found</h1>
        <p className="text-body-sm text-ink-secondary mb-6">This page doesn't exist in the employee portal.</p>
        <Link to="/profile">
          <Button variant="outline">Back to my profile</Button>
        </Link>
      </div>
    </div>
  );
}
