import { useId, useRef, type ChangeEvent } from "react";
import Button from "@/components/common/Button";

interface FileUploadProps {
  label: string;
  hint?: string;
  error?: string;
  accept?: string;
  disabled?: boolean;
  uploading?: boolean;
  onFileSelected: (file: File) => void;
}

/**
 * The first file-upload control in this codebase — no existing component
 * to mirror, so it was built to the same label/hint/error contract as
 * Input/Select/FormField (see those components) rather than inventing a
 * new visual language. A plain hidden `&lt;input type="file"&gt;` triggered by
 * a styled `Button`, not a drag-and-drop zone — keeps behavior fully
 * native/keyboard-accessible with no custom drop-target logic to get
 * wrong. Validation (file type/size) is the caller's responsibility,
 * mirroring every other form component here — the backend remains
 * authoritative (see kyc-service's own validateFile).
 */
export default function FileUpload({ label, hint, error, accept, disabled, uploading, onFileSelected }: FileUploadProps) {
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) {
      onFileSelected(file);
    }
    // Reset so selecting the same file again (e.g. after a rejection) still fires onChange.
    event.target.value = "";
  }

  return (
    <div className="w-full">
      <span className="block text-label text-ink-secondary mb-1.5">{label}</span>
      <div className="flex items-center gap-3">
        <Button
          type="button"
          variant="outline"
          size="sm"
          icon="upload"
          disabled={disabled || uploading}
          loading={uploading}
          onClick={() => inputRef.current?.click()}
        >
          {uploading ? "Uploading..." : "Choose file"}
        </Button>
        <input
          ref={inputRef}
          id={inputId}
          type="file"
          accept={accept}
          className="sr-only"
          aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
          aria-invalid={Boolean(error) || undefined}
          onChange={handleChange}
          disabled={disabled || uploading}
        />
      </div>
      {hint && !error && (
        <p id={hintId} className="mt-1.5 text-caption text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1.5 text-caption text-semantic-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
