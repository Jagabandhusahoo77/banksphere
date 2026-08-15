import { Link } from "react-router-dom";
import { formatDateTime } from "@/utils/format";
import type { KycQueueItem, KycStatus } from "@/types/kyc";
import Card from "@/components/common/Card";
import Badge from "@/components/common/Badge";
import EmptyState from "@/components/common/EmptyState";

const STATUS_TONE: Record<KycStatus, "success" | "warning" | "error" | "info" | "neutral"> = {
  DRAFT: "neutral",
  SUBMITTED: "info",
  UNDER_REVIEW: "info",
  ADDITIONAL_INFORMATION_REQUIRED: "warning",
  RESUBMITTED: "info",
  APPROVED: "success",
  REJECTED: "error",
};

/** Shared by KYC Queue, Document Verification, and Completed Reviews — same table shape, different status filters. */
export default function KycQueueTable({ items, emptyTitle }: { items: KycQueueItem[]; emptyTitle: string }) {
  if (items.length === 0) {
    return <EmptyState title={emptyTitle} message="Nothing to show right now." />;
  }

  return (
    <Card padding="none">
      <div className="table-scroll">
        <table className="w-full text-body-sm">
          <thead>
            <tr className="border-b border-surface-border text-left text-label text-ink-muted uppercase tracking-wide">
              <th className="px-4 py-3">Application</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Documents</th>
              <th className="px-4 py-3">Submitted</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.applicationId} className="border-b border-surface-border last:border-0">
                <td className="px-4 py-3 text-ink-primary font-medium">{item.applicationId.slice(0, 8)}</td>
                <td className="px-4 py-3">
                  <Badge tone={STATUS_TONE[item.status]}>{item.status}</Badge>
                </td>
                <td className="px-4 py-3 text-ink-secondary">
                  {item.documentsSubmitted} / {item.documentsRequired}
                </td>
                <td className="px-4 py-3 text-ink-secondary">
                  {item.submittedAt ? formatDateTime(item.submittedAt) : "—"}
                </td>
                <td className="px-4 py-3 text-right">
                  <Link to={`/kyc/applications/${item.applicationId}`} className="text-brand-primary hover:underline">
                    Review
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
