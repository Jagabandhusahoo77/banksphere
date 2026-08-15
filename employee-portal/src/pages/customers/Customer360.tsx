import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { customer360Service } from "@/services/customer360Service";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import { formatDateTime, formatMoney, maskAccountNumber } from "@/utils/format";
import type { Customer360Response, Customer360Section } from "@/types/customer360";
import Card from "@/components/common/Card";
import Badge from "@/components/common/Badge";
import Spinner from "@/components/common/Spinner";
import ErrorState from "@/components/common/ErrorState";
import { DOCUMENT_TYPE_LABELS, type KycStatus } from "@/types/kyc";

const KYC_STATUS_TONE: Record<KycStatus, "success" | "warning" | "error" | "info" | "neutral"> = {
  DRAFT: "neutral",
  SUBMITTED: "info",
  UNDER_REVIEW: "info",
  ADDITIONAL_INFORMATION_REQUIRED: "warning",
  RESUBMITTED: "info",
  APPROVED: "success",
  REJECTED: "error",
};

/** A section the caller lacks permission for — see Customer360Section's own doc comment for why this is distinct from "no data yet." */
function UnavailableSection({ reason }: { reason: string | null }) {
  return (
    <p className="text-body-sm text-ink-muted italic">
      Not authorized to view this section{reason ? ` (${reason})` : ""}.
    </p>
  );
}

function SectionCard<T>({
  title,
  section,
  render,
  empty,
}: {
  title: string;
  section: Customer360Section<T>;
  render: (data: T) => React.ReactNode;
  empty: string;
}) {
  return (
    <Card title={title}>
      {!section.available ? (
        <UnavailableSection reason={section.unavailableReason} />
      ) : section.data === null || (Array.isArray(section.data) && section.data.length === 0) ? (
        <p className="text-body-sm text-ink-muted">{empty}</p>
      ) : (
        render(section.data)
      )}
    </Card>
  );
}

export default function Customer360() {
  const { customerId } = useParams<{ customerId: string }>();
  const [response, setResponse] = useState<Customer360Response | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!customerId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    customer360Service
      .getCustomer360(customerId)
      .then((data) => {
        if (!cancelled) setResponse(data);
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
  }, [customerId]);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner label="Loading customer" />
      </div>
    );
  }

  if (error || !response) return <ErrorState message={error ?? "Customer not found."} />;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-h2 text-ink-primary">Customer 360</h1>
          <p className="text-body-sm text-ink-secondary mt-1">Consolidated operational view</p>
        </div>
        <Link to="/customers" className="text-body-sm text-brand-primary hover:underline">
          New search
        </Link>
      </div>

      <SectionCard
        title="Customer"
        section={response.customer}
        empty="No profile data."
        render={(customer) => (
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Name</dt>
              <dd className="text-body text-ink-primary mt-1">{customer.firstName} {customer.lastName}</dd>
            </div>
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Status</dt>
              <dd className="mt-1"><Badge tone={customer.status === "ACTIVE" ? "success" : "neutral"}>{customer.status}</Badge></dd>
            </div>
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Email</dt>
              <dd className="text-body text-ink-primary mt-1">{customer.email}</dd>
            </div>
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Phone</dt>
              <dd className="text-body text-ink-primary mt-1">{customer.phone}</dd>
            </div>
            <div>
              <dt className="text-label text-ink-muted uppercase tracking-wide">Customer Since</dt>
              <dd className="text-body text-ink-primary mt-1">{formatDateTime(customer.createdAt)}</dd>
            </div>
          </dl>
        )}
      />

      <SectionCard
        title="Accounts"
        section={response.accounts}
        empty="This customer has no accounts."
        render={(accounts) => (
          <div className="space-y-3">
            {accounts.map((account) => (
              <div key={account.id} className="flex items-center justify-between border border-surface-border rounded-md p-3.5">
                <div>
                  <p className="text-body-sm font-medium text-ink-primary">{maskAccountNumber(account.accountNumber)}</p>
                  <p className="text-caption text-ink-muted">{account.accountType}</p>
                </div>
                <div className="text-right">
                  <p className="text-body-sm text-ink-primary">{formatMoney(account.balance, account.currency)}</p>
                  <Badge tone={account.status === "ACTIVE" ? "success" : "neutral"}>{account.status}</Badge>
                </div>
              </div>
            ))}
          </div>
        )}
      />

      <SectionCard
        title="Recent Transactions"
        section={response.transactions}
        empty="No recent transactions."
        render={(transactions) => (
          <div className="table-scroll">
            <table className="w-full text-body-sm">
              <thead>
                <tr className="border-b border-surface-border text-left text-label text-ink-muted uppercase tracking-wide">
                  <th className="px-3 py-2">Type</th>
                  <th className="px-3 py-2">Amount</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Date</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((txn) => (
                  <tr key={txn.id} className="border-b border-surface-border last:border-0">
                    <td className="px-3 py-2 text-ink-primary">{txn.transactionType}</td>
                    <td className="px-3 py-2 text-ink-primary">{formatMoney(txn.amount, txn.currency)}</td>
                    <td className="px-3 py-2"><Badge tone={txn.status === "COMPLETED" ? "success" : "neutral"}>{txn.status}</Badge></td>
                    <td className="px-3 py-2 text-ink-secondary">{formatDateTime(txn.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      />

      <SectionCard
        title="Beneficiaries"
        section={response.beneficiaries}
        empty="No active beneficiaries."
        render={(beneficiaries) => (
          <div className="space-y-2">
            {beneficiaries.map((b) => (
              <div key={b.id} className="flex items-center justify-between border border-surface-border rounded-md p-3">
                <div>
                  <p className="text-body-sm text-ink-primary">{b.beneficiaryName}</p>
                  <p className="text-caption text-ink-muted">{maskAccountNumber(b.accountNumber)} · {b.bankName}</p>
                </div>
                <Badge tone={b.status === "ACTIVE" ? "success" : "neutral"}>{b.status}</Badge>
              </div>
            ))}
          </div>
        )}
      />

      <SectionCard
        title="KYC"
        section={response.kyc}
        empty="This customer has not started KYC."
        render={(kyc) => (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <Badge tone={KYC_STATUS_TONE[kyc.status]}>{kyc.status}</Badge>
              <Link to={`/kyc/applications/${kyc.id}`} className="text-body-sm text-brand-primary hover:underline">
                Open application
              </Link>
            </div>
            {kyc.submittedAt && <p className="text-body-sm text-ink-secondary">Submitted {formatDateTime(kyc.submittedAt)}</p>}
            {kyc.missingDocumentTypes.length > 0 && (
              <p className="text-body-sm text-ink-secondary">
                Missing: {kyc.missingDocumentTypes.map((t) => DOCUMENT_TYPE_LABELS[t]).join(", ")}
              </p>
            )}
            {kyc.reviewReason && <p className="text-body-sm text-ink-secondary">{kyc.reviewReason}</p>}
          </div>
        )}
      />

      {response.unavailableCapabilities.length > 0 && (
        <Card title="Other Capabilities">
          <div className="flex flex-wrap gap-2">
            {response.unavailableCapabilities.map((capability) => (
              <Badge key={capability} tone="neutral">
                {capability.replaceAll("_", " ")} — not yet available
              </Badge>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
