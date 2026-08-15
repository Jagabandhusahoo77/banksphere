import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { operationsService } from "@/services/operationsService";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import Card from "@/components/common/Card";
import Input from "@/components/common/Input";
import Button from "@/components/common/Button";

/**
 * The search step mirrors CashDeposit's "Find Customer" step exactly —
 * an account number is the identifier an employee realistically has in
 * hand. No standalone name/email search endpoint exists (see
 * docs/architecture/employee-operations.md); a customer-id search is
 * also supported for the case an employee already has the id from
 * elsewhere (e.g. a KYC queue row).
 */
export default function Customer360Search() {
  const navigate = useNavigate();
  const [accountNumber, setAccountNumber] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  async function handleSearch(event: FormEvent) {
    event.preventDefault();
    if (searching || accountNumber.length !== 12) return;

    setSearching(true);
    setSearchError(null);
    try {
      const result = await operationsService.customerSearchByAccountNumber(accountNumber);
      navigate(`/customers/${result.customerId}/360`);
    } catch (err) {
      setSearchError(getFriendlyErrorMessage(err, { 404: "No customer found with that account number." }));
    } finally {
      setSearching(false);
    }
  }

  return (
    <div className="space-y-6 max-w-md">
      <div>
        <h1 className="text-h2 text-ink-primary">Customer 360</h1>
        <p className="text-body-sm text-ink-secondary mt-1">
          Find a customer to see their consolidated accounts, transactions, beneficiaries, and KYC status.
        </p>
      </div>

      <Card title="Find Customer">
        <form onSubmit={handleSearch} className="space-y-4" noValidate>
          <Input
            label="Account Number"
            hint="12-digit BankSphere account number"
            value={accountNumber}
            onChange={(event) => setAccountNumber(event.target.value.replace(/\D/g, "").slice(0, 12))}
            inputMode="numeric"
            maxLength={12}
          />
          {searchError && (
            <p role="alert" className="text-body-sm text-semantic-error bg-semantic-error-light rounded-md px-3 py-2">
              {searchError}
            </p>
          )}
          <Button type="submit" loading={searching} disabled={accountNumber.length !== 12} fullWidth>
            Search
          </Button>
        </form>
      </Card>
    </div>
  );
}
