import { Link } from "react-router-dom";
import Icon from "@/components/common/Icon";
import Button from "@/components/common/Button";

export default function Unauthorized() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-background px-4">
      <div className="text-center max-w-sm">
        <span className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-semantic-error-light text-semantic-error mb-5">
          <Icon name="shield-check" size={28} />
        </span>
        <h1 className="text-h2 text-ink-primary mb-2">Not authorized</h1>
        <p className="text-body-sm text-ink-secondary mb-6">
          Your role doesn't include the permission required for this page. This is enforced by the server on every
          request — if you believe this is wrong, contact an administrator to review your role assignment.
        </p>
        <Link to="/profile">
          <Button variant="outline">Back to my profile</Button>
        </Link>
      </div>
    </div>
  );
}
