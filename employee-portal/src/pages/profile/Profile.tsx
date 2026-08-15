import { useEffect, useState } from "react";
import { employeeAuthService } from "@/services/employeeAuthService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import type { EmployeeResponse } from "@/types/employee";
import Card from "@/components/common/Card";
import Badge from "@/components/common/Badge";
import Spinner from "@/components/common/Spinner";
import ErrorState from "@/components/common/ErrorState";

const STATUS_TONE = {
  ACTIVE: "success",
  INACTIVE: "neutral",
  LOCKED: "error",
} as const;

export default function Profile() {
  const [profile, setProfile] = useState<EmployeeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    // Always re-fetched from GET /api/v1/employees/me rather than reused
    // from the login response — this page exists to exercise the real
    // profile endpoint, and to reflect anything an admin changed since
    // login (e.g. a status change) on next visit.
    employeeAuthService
      .getCurrentEmployee()
      .then((response) => {
        if (!cancelled) setProfile(response);
      })
      .catch((err) => {
        if (!cancelled) setError(getFriendlyErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner label="Loading profile" />
      </div>
    );
  }

  if (error || !profile) {
    return <ErrorState message={error ?? "Couldn't load your profile."} />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-h2 text-ink-primary">My Profile</h1>
        <p className="text-body-sm text-ink-secondary mt-1">Your employee identity and access, as recognized by BankSphere.</p>
      </div>

      <Card title="Identity">
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Name</dt>
            <dd className="text-body text-ink-primary mt-1">
              {profile.firstName} {profile.lastName}
            </dd>
          </div>
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Employee Number</dt>
            <dd className="text-body text-ink-primary mt-1">{profile.employeeNumber}</dd>
          </div>
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Username</dt>
            <dd className="text-body text-ink-primary mt-1">{profile.username}</dd>
          </div>
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Email</dt>
            <dd className="text-body text-ink-primary mt-1">{profile.email}</dd>
          </div>
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Status</dt>
            <dd className="mt-1">
              <Badge tone={STATUS_TONE[profile.status]}>{profile.status}</Badge>
            </dd>
          </div>
        </dl>
      </Card>

      <Card title="Branch">
        <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Branch Name</dt>
            <dd className="text-body text-ink-primary mt-1">{profile.branch.branchName}</dd>
          </div>
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Branch Code</dt>
            <dd className="text-body text-ink-primary mt-1">{profile.branch.branchCode}</dd>
          </div>
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">IFSC</dt>
            <dd className="text-body text-ink-primary mt-1">{profile.branch.ifsc}</dd>
          </div>
        </dl>
      </Card>

      <Card title="Roles">
        <div className="flex flex-wrap gap-2">
          {profile.roles.map((role) => (
            <Badge key={role} tone="brand">
              {role}
            </Badge>
          ))}
        </div>
      </Card>

      <Card title="Permissions" subtitle="Enforced server-side on every request — this list is informational only.">
        <div className="flex flex-wrap gap-2">
          {profile.permissions.map((permission) => (
            <Badge key={permission} tone="neutral">
              {permission}
            </Badge>
          ))}
        </div>
      </Card>
    </div>
  );
}
