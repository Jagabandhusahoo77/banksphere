export default function TypingIndicator() {
  return (
    <div className="flex justify-start" role="status" aria-label="BankSphere Assistant is typing">
      <div className="flex items-center gap-1 rounded-lg rounded-bl-sm bg-surface-muted px-3.5 py-3">
        <span className="w-1.5 h-1.5 rounded-full bg-ink-muted animate-bounce [animation-delay:-0.3s]" />
        <span className="w-1.5 h-1.5 rounded-full bg-ink-muted animate-bounce [animation-delay:-0.15s]" />
        <span className="w-1.5 h-1.5 rounded-full bg-ink-muted animate-bounce" />
      </div>
    </div>
  );
}
