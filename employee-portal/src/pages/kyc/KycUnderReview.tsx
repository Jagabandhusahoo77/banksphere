import { useEffect, useState } from "react";
import { kycService } from "@/services/kycService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import type { KycQueueItem } from "@/types/kyc";
import Spinner from "@/components/common/Spinner";
import ErrorState from "@/components/common/ErrorState";
import KycQueueTable from "@/components/kyc/KycQueueTable";

/** "Document Verification" in the nav — applications currently under review, where document verify/reject actually happens. */
export default function KycUnderReview() {
  const [items, setItems] = useState<KycQueueItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    kycService
      .getQueue("UNDER_REVIEW")
      .then((response) => {
        if (!cancelled) setItems(response);
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
        <h1 className="text-h2 text-ink-primary">Document Verification</h1>
        <p className="text-body-sm text-ink-secondary mt-1">Applications currently under review — verify or reject each document.</p>
      </div>

      {loading && (
        <div className="flex justify-center py-16">
          <Spinner label="Loading applications under review" />
        </div>
      )}
      {!loading && error && <ErrorState message={error} />}
      {!loading && !error && items && <KycQueueTable items={items} emptyTitle="No applications currently under review" />}
    </div>
  );
}
