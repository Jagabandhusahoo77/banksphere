import { useState, type FormEvent } from "react";
import Icon from "@/components/common/Icon";

interface ChatInputProps {
  onSend: (text: string) => void;
  disabled?: boolean;
}

export default function ChatInput({ onSend, disabled }: ChatInputProps) {
  const [value, setValue] = useState("");

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setValue("");
  };

  return (
    <form onSubmit={handleSubmit} className="flex items-center gap-2 p-3 border-t border-surface-border">
      <label htmlFor="chat-input" className="sr-only">
        Message BankSphere Assistant
      </label>
      <input
        id="chat-input"
        type="text"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        disabled={disabled}
        placeholder="Ask a question…"
        className="flex-1 h-10 px-3 text-body-sm rounded-md border border-surface-border focus-visible:border-brand-primary disabled:bg-surface-muted"
      />
      <button
        type="submit"
        disabled={disabled || !value.trim()}
        aria-label="Send message"
        className="flex items-center justify-center w-10 h-10 rounded-md bg-brand-primary text-white disabled:opacity-40"
      >
        <Icon name="arrow-right" size={18} />
      </button>
    </form>
  );
}
