import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { kycService } from "@/services/kycService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import { formatDateTime } from "@/utils/format";
import { DOCUMENT_TYPE_LABELS, type KycApplicationDetail, type KycStatus } from "@/types/kyc";
import Card from "@/components/common/Card";
import Badge from "@/components/common/Badge";
import Button from "@/components/common/Button";
import Spinner from "@/components/common/Spinner";
import ErrorState from "@/components/common/ErrorState";

const STATUS_TONE: Record<KycStatus, "success" | "warning" | "error" | "info" | "neutral"> = {
  DRAFT: "neutral",
  SUBMITTED: "info",
  UNDER_REVIEW: "info",
  ADDITIONAL_INFORMATION_REQUIRED: "warning",
  RESUBMITTED: "info",
  APPROVED: "success",
  REJECTED: "error",
};

type ReasonAction = { kind: "reject-document"; documentId: string } | { kind: "request-information" } | { kind: "reject-application" };

export default function KycReviewApplication() {
  const { id } = useParams<{ id: string }>();
  const { hasPermission } = useAuth();

  const [application, setApplication] = useState<KycApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  const [actionInFlight, setActionInFlight] = useState<string | null>(null);

  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null);
  const [reasonText, setReasonText] = useState("");

  const load = useCallback(() => {
    if (!id) return;
    setLoading(true);
    setError(null);
    kycService
      .getApplication(id)
      .then(setApplication)
      .catch((err) => setError(getFriendlyErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner label="Loading application" />
      </div>
    );
  }
  if (error || !application) return <ErrorState message={error ?? "Application not found."} onRetry={load} />;

  const canReview = hasPermission("KYC_REVIEW");
  const canApprove = hasPermission("KYC_APPROVE");
  const canReject = hasPermission("KYC_REJECT");

  async function runAction(key: string, action: () => Promise<KycApplicationDetail>) {
    setActionInFlight(key);
    setActionError(null);
    try {
      const updated = await action();
      setApplication(updated);
      setReasonAction(null);
      setReasonText("");
    } catch (err) {
      setActionError(
        getFriendlyErrorMessage(err, {
          409: "This application was just updated by another reviewer — refreshing.",
          422: "This action isn't valid for the application's current status.",
        }),
      );
      load();
    } finally {
      setActionInFlight(null);
    }
  }

  async function submitReason() {
    if (!reasonAction || !reasonText.trim() || !application) return;
    if (reasonAction.kind === "reject-document") {
      await runAction("reject-document", () =>
        kycService.rejectDocument(reasonAction.documentId, reasonText.trim()).then(() => kycService.getApplication(application.id)),
      );
    } else if (reasonAction.kind === "request-information") {
      await runAction("request-information", () => kycService.requestInformation(application.id, reasonText.trim()));
    } else {
      await runAction("reject-application", () => kycService.reject(application.id, reasonText.trim()));
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-h2 text-ink-primary">KYC Application</h1>
          <p className="text-body-sm text-ink-secondary mt-1">{application.id}</p>
        </div>
        <Link to={`/customers/${application.customerId}/360`} className="text-body-sm text-brand-primary hover:underline">
          View Customer 360
        </Link>
      </div>

      {actionError && (
        <p role="alert" className="text-body-sm text-semantic-error bg-semantic-error-light rounded-md px-3.5 py-2.5">
          {actionError}
        </p>
      )}

      <Card>
        <div className="flex items-center justify-between mb-4">
          <Badge tone={STATUS_TONE[application.status]}>{application.status}</Badge>
          {application.submittedAt && (
            <p className="text-caption text-ink-muted">Submitted {formatDateTime(application.submittedAt)}</p>
          )}
        </div>
        <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">PAN</dt>
            <dd className="text-body text-ink-primary mt-1">{application.panNumber}</dd>
          </div>
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Occupation</dt>
            <dd className="text-body text-ink-primary mt-1">{application.occupation}</dd>
          </div>
          <div>
            <dt className="text-label text-ink-muted uppercase tracking-wide">Annual Income</dt>
            <dd className="text-body text-ink-primary mt-1">{application.annualIncomeRange}</dd>
          </div>
        </dl>

        {(application.status === "SUBMITTED" || application.status === "RESUBMITTED") && canReview && (
          <div className="mt-5 pt-4 border-t border-surface-border">
            <Button
              size="sm"
              loading={actionInFlight === "start-review"}
              onClick={() => runAction("start-review", () => kycService.startReview(application.id))}
            >
              Start Review
            </Button>
          </div>
        )}
      </Card>

      <Card title="Documents">
        <div className="space-y-3">
          {application.documents.map((doc) => (
            <div key={doc.id} className="border border-surface-border rounded-md p-3.5">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-body-sm font-medium text-ink-primary">{DOCUMENT_TYPE_LABELS[doc.documentType]}</p>
                  <p className="text-caption text-ink-muted">{doc.originalFileName}</p>
                </div>
                <Badge tone={doc.documentStatus === "VERIFIED" ? "success" : doc.documentStatus === "REJECTED" ? "error" : "neutral"}>
                  {doc.documentStatus}
                </Badge>
              </div>
              {doc.rejectionReason && <p className="text-caption text-semantic-error mt-1.5">{doc.rejectionReason}</p>}

              {application.status === "UNDER_REVIEW" && canReview && doc.documentStatus === "PENDING" && (
                <div className="flex gap-2 mt-3">
                  <Button
                    size="sm"
                    variant="outline"
                    loading={actionInFlight === `verify-${doc.id}`}
                    onClick={() => runAction(`verify-${doc.id}`, () => kycService.verifyDocument(doc.id).then(() => kycService.getApplication(application.id)))}
                  >
                    Verify
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      setReasonAction({ kind: "reject-document", documentId: doc.id });
                      setReasonText("");
                    }}
                  >
                    Reject
                  </Button>
                </div>
              )}
            </div>
          ))}
          {application.documents.length === 0 && <p className="text-body-sm text-ink-muted">No documents uploaded yet.</p>}
        </div>
      </Card>

      {reasonAction && (
        <Card
          title={
            reasonAction.kind === "reject-document"
              ? "Reject Document"
              : reasonAction.kind === "request-information"
                ? "Request Additional Information"
                : "Reject Application"
          }
        >
          <div className="space-y-3">
            <label className="block text-label text-ink-secondary" htmlFor="reason-text">
              Reason (shown to the customer)
            </label>
            <textarea
              id="reason-text"
              className="w-full min-h-24 px-3.5 py-2.5 text-body text-ink-primary bg-white border border-surface-border rounded-md focus-visible:border-brand-primary"
              value={reasonText}
              onChange={(e) => setReasonText(e.target.value)}
              maxLength={1000}
            />
            <div className="flex gap-3">
              <Button variant="ghost" size="sm" onClick={() => setReasonAction(null)}>
                Cancel
              </Button>
              <Button
                size="sm"
                variant="danger"
                disabled={!reasonText.trim()}
                loading={actionInFlight !== null}
                onClick={submitReason}
              >
                Confirm
              </Button>
            </div>
          </div>
        </Card>
      )}

      {application.status === "UNDER_REVIEW" && (
        <Card title="Decision">
          <div className="flex flex-wrap gap-3">
            {canReview && (
              <Button
                variant="outline"
                loading={actionInFlight === "request-information"}
                onClick={() => {
                  setReasonAction({ kind: "request-information" });
                  setReasonText("");
                }}
              >
                Request Additional Information
              </Button>
            )}
            {canApprove && (
              <Button
                loading={actionInFlight === "approve"}
                disabled={application.missingDocumentTypes.length > 0}
                onClick={() => runAction("approve", () => kycService.approve(application.id))}
              >
                Approve Application
              </Button>
            )}
            {canReject && (
              <Button
                variant="danger"
                onClick={() => {
                  setReasonAction({ kind: "reject-application" });
                  setReasonText("");
                }}
              >
                Reject Application
              </Button>
            )}
          </div>
          {application.missingDocumentTypes.length > 0 && (
            <p className="text-caption text-ink-muted mt-2">
              Cannot approve — missing: {application.missingDocumentTypes.map((t) => DOCUMENT_TYPE_LABELS[t]).join(", ")}
            </p>
          )}
        </Card>
      )}

      {application.statusHistory.length > 0 && (
        <Card title="Review History">
          <ul className="space-y-2">
            {application.statusHistory.map((entry, index) => (
              <li key={index} className="text-body-sm text-ink-secondary border-b border-surface-border last:border-0 pb-2 last:pb-0">
                <span className="text-ink-primary font-medium">{entry.fromStatus ?? "—"} → {entry.toStatus}</span>{" "}
                <span className="text-caption text-ink-muted">{formatDateTime(entry.changedAt)}</span>
                {entry.reason && <p className="text-caption text-ink-muted mt-0.5">{entry.reason}</p>}
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
