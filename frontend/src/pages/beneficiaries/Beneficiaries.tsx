import { useState } from "react";
import { useBeneficiaries } from "@/hooks/useBeneficiaries";
import { beneficiaryService } from "@/services/beneficiaryService";
import { useToast } from "@/components/common/Toast";
import Card from "@/components/common/Card";
import Button from "@/components/common/Button";
import Badge from "@/components/common/Badge";
import Icon from "@/components/common/Icon";
import Input from "@/components/common/Input";
import Modal from "@/components/common/Modal";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";
import { SkeletonCard } from "@/components/common/Skeleton";
import { maskAccountNumber } from "@/utils/format";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import type { Beneficiary } from "@/types/beneficiary";

interface FormState {
  beneficiaryName: string;
  accountNumber: string;
  ifsc: string;
  bankName: string;
  nickname: string;
}

const EMPTY_FORM: FormState = { beneficiaryName: "", accountNumber: "", ifsc: "", bankName: "", nickname: "" };

const ACCOUNT_NUMBER_PATTERN = /^\d{9,18}$/;
const IFSC_PATTERN = /^[A-Z]{4}0[A-Z0-9]{6}$/;

/**
 * Client-side checks mirror the backend's Bean Validation rules for
 * friendlier, immediate feedback — the backend (beneficiary-service's
 * CreateBeneficiaryRequest/UpdateBeneficiaryRequest) remains the
 * authoritative validator; any rule drift surfaces as a real 400 from the
 * API, still shown to the user, rather than being silently accepted here.
 */
function validate(form: FormState, mode: "add" | "edit"): Partial<Record<keyof FormState, string>> {
  const errors: Partial<Record<keyof FormState, string>> = {};
  if (!form.beneficiaryName.trim()) errors.beneficiaryName = "Beneficiary name is required.";
  else if (form.beneficiaryName.length > 100) errors.beneficiaryName = "Must be at most 100 characters.";

  if (mode === "add") {
    if (!form.accountNumber.trim()) errors.accountNumber = "Account number is required.";
    else if (!ACCOUNT_NUMBER_PATTERN.test(form.accountNumber.trim())) errors.accountNumber = "Must be 9 to 18 digits.";

    if (!form.ifsc.trim()) errors.ifsc = "IFSC code is required.";
    else if (!IFSC_PATTERN.test(form.ifsc.trim().toUpperCase())) errors.ifsc = "Must be a valid 11-character IFSC, e.g. BANK0001234.";
  }

  if (!form.bankName.trim()) errors.bankName = "Bank name is required.";
  else if (form.bankName.length > 150) errors.bankName = "Must be at most 150 characters.";

  if (form.nickname.length > 50) errors.nickname = "Must be at most 50 characters.";

  return errors;
}

export default function Beneficiaries() {
  const { beneficiaries, loading, error, reload } = useBeneficiaries(true);
  const { showToast } = useToast();

  const [modalOpen, setModalOpen] = useState(false);
  const [mode, setMode] = useState<"add" | "edit">("add");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof FormState, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [deactivateTarget, setDeactivateTarget] = useState<Beneficiary | null>(null);
  const [deactivating, setDeactivating] = useState(false);

  const openAddModal = () => {
    setMode("add");
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  };

  const openEditModal = (beneficiary: Beneficiary) => {
    setMode("edit");
    setEditingId(beneficiary.id);
    setForm({
      beneficiaryName: beneficiary.beneficiaryName,
      accountNumber: beneficiary.accountNumber,
      ifsc: beneficiary.ifsc,
      bankName: beneficiary.bankName,
      nickname: beneficiary.nickname ?? "",
    });
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  };

  const handleFieldChange = (field: keyof FormState, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    const errors = validate(form, mode);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setFormError(null);
    setSubmitting(true);
    try {
      if (mode === "add") {
        await beneficiaryService.createBeneficiary({
          beneficiaryName: form.beneficiaryName.trim(),
          accountNumber: form.accountNumber.trim(),
          ifsc: form.ifsc.trim().toUpperCase(),
          bankName: form.bankName.trim(),
          nickname: form.nickname.trim() || undefined,
        });
        showToast("Beneficiary added.", "success");
      } else if (editingId) {
        await beneficiaryService.updateBeneficiary(editingId, {
          beneficiaryName: form.beneficiaryName.trim(),
          bankName: form.bankName.trim(),
          nickname: form.nickname.trim() || undefined,
        });
        showToast("Beneficiary updated.", "success");
      }
      setModalOpen(false);
      reload();
    } catch (err) {
      setFormError(
        getFriendlyErrorMessage(err, {
          409: "You already have an active beneficiary with this account number and IFSC.",
        }),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeactivate = async () => {
    if (!deactivateTarget || deactivating) return;
    setDeactivating(true);
    try {
      await beneficiaryService.deactivateBeneficiary(deactivateTarget.id);
      showToast(`${deactivateTarget.beneficiaryName} removed from beneficiaries.`, "success");
      setDeactivateTarget(null);
      reload();
    } catch (err) {
      showToast(getFriendlyErrorMessage(err), "error");
    } finally {
      setDeactivating(false);
    }
  };

  if (error) {
    return <ErrorState title="Couldn't load beneficiaries" message={error} onRetry={reload} />;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-h1 text-ink-primary">Beneficiaries</h1>
          <p className="mt-1 text-body text-ink-secondary">People and accounts you send money to.</p>
        </div>
        <Button variant="primary" icon="plus" onClick={openAddModal}>
          Add beneficiary
        </Button>
      </div>

      {loading ? (
        <SkeletonCard />
      ) : beneficiaries.length === 0 ? (
        <Card>
          <EmptyState
            title="No beneficiaries yet"
            description="Add someone's bank details to send them money faster next time."
            action={
              <Button variant="primary" icon="plus" onClick={openAddModal}>
                Add beneficiary
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {beneficiaries.map((beneficiary) => (
            <Card key={beneficiary.id}>
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3 min-w-0">
                  <span className="flex items-center justify-center w-10 h-10 rounded-full bg-brand-primary-light text-brand-primary shrink-0">
                    <Icon name="users" size={18} />
                  </span>
                  <div className="min-w-0">
                    <p className="text-body font-medium text-ink-primary truncate">
                      {beneficiary.nickname || beneficiary.beneficiaryName}
                    </p>
                    {beneficiary.nickname && (
                      <p className="text-caption text-ink-muted truncate">{beneficiary.beneficiaryName}</p>
                    )}
                    <p className="text-body-sm text-ink-secondary mt-1">{beneficiary.bankName}</p>
                    <p className="font-mono text-caption text-ink-muted mt-0.5">
                      {maskAccountNumber(beneficiary.accountNumber)} · {beneficiary.ifsc}
                    </p>
                  </div>
                </div>
                <Badge tone={beneficiary.status === "ACTIVE" ? "success" : "neutral"}>{beneficiary.status}</Badge>
              </div>
              <div className="flex items-center gap-2 mt-4 pt-4 border-t border-surface-border">
                <Button variant="outline" size="sm" onClick={() => openEditModal(beneficiary)}>
                  Edit
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setDeactivateTarget(beneficiary)}>
                  Remove
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => !submitting && setModalOpen(false)} title={mode === "add" ? "Add beneficiary" : "Edit beneficiary"}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="Beneficiary name"
            value={form.beneficiaryName}
            onChange={(e) => handleFieldChange("beneficiaryName", e.target.value)}
            error={fieldErrors.beneficiaryName}
          />
          <Input
            label="Nickname (optional)"
            value={form.nickname}
            onChange={(e) => handleFieldChange("nickname", e.target.value)}
            error={fieldErrors.nickname}
          />
          <Input
            label="Account number"
            value={form.accountNumber}
            onChange={(e) => handleFieldChange("accountNumber", e.target.value)}
            error={fieldErrors.accountNumber}
            disabled={mode === "edit"}
            hint={mode === "edit" ? "Account number can't be changed — remove and re-add instead." : undefined}
          />
          <Input
            label="IFSC code"
            value={form.ifsc}
            onChange={(e) => handleFieldChange("ifsc", e.target.value.toUpperCase())}
            error={fieldErrors.ifsc}
            disabled={mode === "edit"}
            hint={mode === "edit" ? "IFSC can't be changed — remove and re-add instead." : undefined}
          />
          <Input
            label="Bank name"
            value={form.bankName}
            onChange={(e) => handleFieldChange("bankName", e.target.value)}
            error={fieldErrors.bankName}
          />

          {formError && (
            <p role="alert" className="text-body-sm text-semantic-error">
              {formError}
            </p>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={() => setModalOpen(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              {mode === "add" ? "Add beneficiary" : "Save changes"}
            </Button>
          </div>
        </form>
      </Modal>

      <Modal open={deactivateTarget !== null} onClose={() => setDeactivateTarget(null)} title="Remove beneficiary">
        <p className="text-body-sm text-ink-secondary">
          Remove <strong>{deactivateTarget?.beneficiaryName}</strong> from your beneficiaries? You can add them again
          later if needed.
        </p>
        <div className="flex justify-end gap-2 mt-5">
          <Button variant="ghost" onClick={() => setDeactivateTarget(null)} disabled={deactivating}>
            Cancel
          </Button>
          <Button variant="danger" onClick={handleDeactivate} loading={deactivating}>
            Remove
          </Button>
        </div>
      </Modal>
    </div>
  );
}
