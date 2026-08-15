import { useEffect, useState } from "react";
import { kycService } from "@/services/kycService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import type { KycQueueItem } from "@/types/kyc";
import Spinner from "@/components/common/Spinner";
import ErrorState from "@/components/common/ErrorState";
import KycQueueTable from "@/components/kyc/KycQueueTable";

const QUEUE_STATUSES = new Set(["SUBMITTED", "RESUBMITTED", "ADDITIONAL_INFORMATION_REQUIRED"]);

export default function KycQueue() {
  const [items, setItems] = useState<KycQueueItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    kycService
      .getQueue()
      .then((response) => {
        if (!cancelled) setItems(response.filter((item) => QUEUE_STATUSES.has(item.status)));
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

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-h2 text-ink-primary">KYC Queue</h1>
        <p className="text-body-sm text-ink-secondary mt-1">
          Applications submitted and awaiting review, or awaiting additional information from the customer.
        </p>
      </div>

      {loading && (
        <div className="flex justify-center py-16">
          <Spinner label="Loading queue" />
        </div>
      )}
      {!loading && error && <ErrorState message={error} />}
      {!loading && !error && items && <KycQueueTable items={items} emptyTitle="No applications in the queue" />}
    </div>
  );
}
