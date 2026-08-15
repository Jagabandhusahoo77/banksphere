import { useState } from "react";
import { useKycApplication } from "@/hooks/useKycApplication";
import { kycService } from "@/services/kycService";
import { useToast } from "@/components/common/Toast";
import Card from "@/components/common/Card";
import Button from "@/components/common/Button";
import Badge from "@/components/common/Badge";
import Icon from "@/components/common/Icon";
import Input from "@/components/common/Input";
import Select from "@/components/common/Select";
import ErrorState from "@/components/common/ErrorState";
import { SkeletonCard } from "@/components/common/Skeleton";
import FileUpload from "@/components/forms/FileUpload";
import { formatDateTime } from "@/utils/format";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import { DOCUMENT_TYPES, DOCUMENT_TYPE_LABELS, type CreateKycApplicationRequest, type DocumentType, type KycStatus } from "@/types/kyc";

const STATUS_TONE: Record<KycStatus, "success" | "warning" | "error" | "info" | "neutral"> = {
  DRAFT: "neutral",
  SUBMITTED: "info",
  UNDER_REVIEW: "info",
  ADDITIONAL_INFORMATION_REQUIRED: "warning",
  RESUBMITTED: "info",
  APPROVED: "success",
  REJECTED: "error",
};

const STATUS_LABEL: Record<KycStatus, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  UNDER_REVIEW: "Under Review",
  ADDITIONAL_INFORMATION_REQUIRED: "Additional Information Required",
  RESUBMITTED: "Resubmitted",
  APPROVED: "Approved",
  REJECTED: "Rejected",
};

const ALLOWED_FILE_TYPES = "application/pdf,image/jpeg,image/png";

const INCOME_RANGES = ["Below 5L", "5-10L", "10-25L", "25-50L", "Above 50L"];

interface FormState {
  panNumber: string;
  occupation: string;
  annualIncomeRange: string;
}

const EMPTY_FORM: FormState = { panNumber: "", occupation: "", annualIncomeRange: "" };

const PAN_PATTERN = /^[A-Z]{5}[0-9]{4}[A-Z]$/;

/** Client-side checks mirror kyc-service's CreateKycApplicationRequest Bean Validation — the backend remains authoritative. */
function validate(form: FormState): Partial<Record<keyof FormState, string>> {
  const errors: Partial<Record<keyof FormState, string>> = {};
  const pan = form.panNumber.trim().toUpperCase();
  if (!pan) errors.panNumber = "PAN number is required.";
  else if (!PAN_PATTERN.test(pan)) errors.panNumber = "Must be a well-formed PAN, e.g. ABCDE1234F.";

  if (!form.occupation.trim()) errors.occupation = "Occupation is required.";
  else if (form.occupation.length > 100) errors.occupation = "Must be at most 100 characters.";

  if (!form.annualIncomeRange) errors.annualIncomeRange = "Annual income range is required.";

  return errors;
}

export default function Kyc() {
  const { application, loading, error, reload } = useKycApplication();
  const { showToast } = useToast();

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof FormState, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [uploadingType, setUploadingType] = useState<DocumentType | null>(null);
  const [uploadErrors, setUploadErrors] = useState<Partial<Record<DocumentType, string>>>({});

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  async function handleCreate() {
    const errors = validate(form);
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) return;

    setCreating(true);
    try {
      const request: CreateKycApplicationRequest = {
        panNumber: form.panNumber.trim().toUpperCase(),
        occupation: form.occupation.trim(),
        annualIncomeRange: form.annualIncomeRange,
      };
      await kycService.createApplication(request);
      showToast("KYC application started.", "success");
      reload();
    } catch (err) {
      setFormError(getFriendlyErrorMessage(err, { 409: "You already have a KYC application in progress." }));
    } finally {
      setCreating(false);
    }
  }

  async function handleUpload(documentType: DocumentType, file: File) {
    if (!application) return;
    setUploadingType(documentType);
    setUploadErrors((current) => ({ ...current, [documentType]: undefined }));
    try {
      await kycService.uploadDocument(application.id, documentType, file);
      showToast(`${DOCUMENT_TYPE_LABELS[documentType]} uploaded.`, "success");
      reload();
    } catch (err) {
      setUploadErrors((current) => ({
        ...current,
        [documentType]: getFriendlyErrorMessage(err, {
          400: "Unsupported file — please upload a PDF, JPEG, or PNG under 5MB.",
          409: "A document of this type has already been submitted.",
        }),
      }));
    } finally {
      setUploadingType(null);
    }
  }

  async function handleSubmit(isResubmit: boolean) {
    if (!application) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      if (isResubmit) {
        await kycService.resubmit(application.id);
      } else {
        await kycService.submit(application.id);
      }
      showToast("KYC application submitted for review.", "success");
      reload();
    } catch (err) {
      setSubmitError(
        getFriendlyErrorMessage(err, {
          422: "Please upload all required documents before submitting.",
        }),
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <SkeletonCard />;
  if (error) return <ErrorState message={error} onRetry={reload} />;

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-h1 text-ink-primary">KYC Verification</h1>
        <p className="text-body text-ink-secondary mt-1">
          Complete your Know Your Customer (KYC) verification to unlock the full range of BankSphere services.
        </p>
      </div>

      {!application && (
        <Card title="Start Your KYC Application">
          <div className="space-y-4">
            <Input
              label="PAN Number"
              placeholder="ABCDE1234F"
              value={form.panNumber}
              maxLength={10}
              onChange={(e) => setForm({ ...form, panNumber: e.target.value.toUpperCase() })}
              error={fieldErrors.panNumber}
            />
            <Input
              label="Occupation"
              placeholder="e.g. Software Engineer"
              value={form.occupation}
              onChange={(e) => setForm({ ...form, occupation: e.target.value })}
              error={fieldErrors.occupation}
            />
            <Select
              label="Annual Income Range"
              value={form.annualIncomeRange}
              onChange={(e) => setForm({ ...form, annualIncomeRange: e.target.value })}
              error={fieldErrors.annualIncomeRange}
            >
              <option value="">Select a range</option>
              {INCOME_RANGES.map((range) => (
                <option key={range} value={range}>
                  {range}
                </option>
              ))}
            </Select>
            {formError && (
              <p className="text-body-sm text-semantic-error bg-semantic-error-light rounded-md px-3.5 py-2.5" role="alert">
                {formError}
              </p>
            )}
            <Button onClick={handleCreate} loading={creating} fullWidth>
              Start Application
            </Button>
          </div>
        </Card>
      )}

      {application && (
        <Card>
          <div className="flex items-center justify-between mb-4">
            <div>
              <p className="text-label text-ink-muted uppercase tracking-wide">Application Status</p>
              <p className="text-h3 text-ink-primary mt-1">{STATUS_LABEL[application.status]}</p>
            </div>
            <Badge tone={STATUS_TONE[application.status]}>{application.status}</Badge>
          </div>

          <dl className="grid grid-cols-2 gap-4 text-body-sm border-t border-surface-border pt-4">
            <div>
              <dt className="text-ink-muted">PAN Number</dt>
              <dd className="text-ink-primary mt-0.5">{application.panNumber}</dd>
            </div>
            <div>
              <dt className="text-ink-muted">Occupation</dt>
              <dd className="text-ink-primary mt-0.5">{application.occupation}</dd>
            </div>
            {application.submittedAt && (
              <div>
                <dt className="text-ink-muted">Submitted</dt>
                <dd className="text-ink-primary mt-0.5">{formatDateTime(application.submittedAt)}</dd>
              </div>
            )}
            {application.reviewedAt && (
              <div>
                <dt className="text-ink-muted">Reviewed</dt>
                <dd className="text-ink-primary mt-0.5">{formatDateTime(application.reviewedAt)}</dd>
              </div>
            )}
          </dl>

          {application.status === "APPROVED" && (
            <div className="flex items-start gap-3 mt-5 p-4 rounded-md bg-semantic-success-light">
              <Icon name="check-circle" size={20} className="text-semantic-success shrink-0 mt-0.5" />
              <p className="text-body-sm text-ink-primary">
                Your KYC verification is complete. You now have full access to BankSphere services.
              </p>
            </div>
          )}

          {application.status === "REJECTED" && (
            <div className="flex items-start gap-3 mt-5 p-4 rounded-md bg-semantic-error-light">
              <Icon name="alert-circle" size={20} className="text-semantic-error shrink-0 mt-0.5" />
              <div>
                <p className="text-body-sm text-ink-primary font-medium">Your KYC application was rejected.</p>
                {application.reviewReason && (
                  <p className="text-body-sm text-ink-secondary mt-1">{application.reviewReason}</p>
                )}
              </div>
            </div>
          )}

          {(application.status === "SUBMITTED" || application.status === "UNDER_REVIEW" || application.status === "RESUBMITTED") && (
            <div className="flex items-start gap-3 mt-5 p-4 rounded-md bg-semantic-info-light">
              <Icon name="clock" size={20} className="text-semantic-info shrink-0 mt-0.5" />
              <p className="text-body-sm text-ink-primary">
                Your application is being reviewed by our team. We'll notify you once a decision has been made.
              </p>
            </div>
          )}

          {application.status === "ADDITIONAL_INFORMATION_REQUIRED" && application.reviewReason && (
            <div className="flex items-start gap-3 mt-5 p-4 rounded-md bg-semantic-warning-light">
              <Icon name="alert-circle" size={20} className="text-semantic-warning shrink-0 mt-0.5" />
              <div>
                <p className="text-body-sm text-ink-primary font-medium">Additional information requested</p>
                <p className="text-body-sm text-ink-secondary mt-1">{application.reviewReason}</p>
              </div>
            </div>
          )}

          {(application.status === "DRAFT" || application.status === "ADDITIONAL_INFORMATION_REQUIRED") && (
            <div className="mt-6 pt-5 border-t border-surface-border space-y-5">
              <p className="text-label text-ink-secondary uppercase tracking-wide">Required Documents</p>
              {DOCUMENT_TYPES.map((documentType) => {
                const existing = application.documents.find(
                  (doc) => doc.documentType === documentType && doc.documentStatus !== "REJECTED",
                );
                const rejected = application.documents.find(
                  (doc) => doc.documentType === documentType && doc.documentStatus === "REJECTED",
                );
                return (
                  <div key={documentType}>
                    {existing ? (
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                          <Icon name="file" size={18} className="text-ink-muted" />
                          <div>
                            <p className="text-body-sm text-ink-primary">{DOCUMENT_TYPE_LABELS[documentType]}</p>
                            <p className="text-caption text-ink-muted">{existing.originalFileName}</p>
                          </div>
                        </div>
                        <Badge tone={existing.documentStatus === "VERIFIED" ? "success" : "neutral"}>
                          {existing.documentStatus}
                        </Badge>
                      </div>
                    ) : (
                      <>
                        {rejected?.rejectionReason && (
                          <p className="text-caption text-semantic-error mb-1.5">
                            Previous upload rejected: {rejected.rejectionReason}
                          </p>
                        )}
                        <FileUpload
                          label={DOCUMENT_TYPE_LABELS[documentType]}
                          hint="PDF, JPEG, or PNG, up to 5MB."
                          accept={ALLOWED_FILE_TYPES}
                          uploading={uploadingType === documentType}
                          disabled={uploadingType !== null}
                          error={uploadErrors[documentType]}
                          onFileSelected={(file) => handleUpload(documentType, file)}
                        />
                      </>
                    )}
                  </div>
                );
              })}

              {submitError && (
                <p className="text-body-sm text-semantic-error bg-semantic-error-light rounded-md px-3.5 py-2.5" role="alert">
                  {submitError}
                </p>
              )}

              <Button
                onClick={() => handleSubmit(application.status === "ADDITIONAL_INFORMATION_REQUIRED")}
                loading={submitting}
                disabled={application.missingDocumentTypes.length > 0}
                fullWidth
              >
                {application.status === "ADDITIONAL_INFORMATION_REQUIRED" ? "Resubmit Application" : "Submit Application"}
              </Button>
              {application.missingDocumentTypes.length > 0 && (
                <p className="text-caption text-ink-muted text-center">
                  Upload all required documents above to enable submission.
                </p>
              )}
            </div>
          )}
        </Card>
      )}
    </div>
  );
}
