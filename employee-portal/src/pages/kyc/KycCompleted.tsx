import { useEffect, useState } from "react";
import { kycService } from "@/services/kycService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import type { KycQueueItem } from "@/types/kyc";
import Spinner from "@/components/common/Spinner";
import ErrorState from "@/components/common/ErrorState";
import KycQueueTable from "@/components/kyc/KycQueueTable";

const COMPLETED_STATUSES = new Set(["APPROVED", "REJECTED"]);

export default function KycCompleted() {
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
        if (!cancelled) setItems(response.filter((item) => COMPLETED_STATUSES.has(item.status)));
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
        <h1 className="text-h2 text-ink-primary">Completed Reviews</h1>
        <p className="text-body-sm text-ink-secondary mt-1">Approved and rejected KYC applications.</p>
      </div>

      {loading && (
        <div className="flex justify-center py-16">
          <Spinner label="Loading completed reviews" />
        </div>
      )}
      {!loading && error && <ErrorState message={error} />}
      {!loading && !error && items && <KycQueueTable items={items} emptyTitle="No completed reviews yet" />}
    </div>
  );
}
