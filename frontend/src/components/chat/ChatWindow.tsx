import { useEffect, useRef } from "react";
import type { ChatMessage as ChatMessageType } from "@/services/chatService";
import ChatMessage from "./ChatMessage";
import ChatInput from "./ChatInput";
import QuickQuestions from "./QuickQuestions";
import TypingIndicator from "./TypingIndicator";
import Logo from "@/components/navigation/Logo";
import Icon from "@/components/common/Icon";

interface ChatWindowProps {
  messages: ChatMessageType[];
  sending: boolean;
  onSend: (text: string) => void;
  onClose: () => void;
}

export default function ChatWindow({ messages, sending, onSend, onClose }: ChatWindowProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, sending]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  return (
    <div
      role="region"
      aria-label="BankSphere Assistant chat"
      className="flex flex-col w-[calc(100vw-2rem)] max-w-sm h-[32rem] max-h-[calc(100vh-7rem)] bg-white rounded-lg shadow-elevation-4 border border-surface-border overflow-hidden"
    >
      <div className="flex items-center justify-between px-4 py-3 bg-brand-primary-dark">
        <div className="flex items-center gap-2">
          <Logo variant="white" to="/" className="h-5" />
          <span className="text-body-sm text-white/70">Assistant</span>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close chat"
          className="text-white/70 hover:text-white rounded-md p-1 -m-1"
        >
          <Icon name="close" size={18} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-3">
        {messages.map((message) => (
          <ChatMessage key={message.id} message={message} />
        ))}
        {sending && <TypingIndicator />}
        <div ref={messagesEndRef} />
      </div>

      <QuickQuestions onSelect={onSend} disabled={sending} />
      <ChatInput onSend={onSend} disabled={sending} />

      <p className="px-4 pb-3 text-caption text-ink-muted">
        BankSphere Assistant gives general information only and can't access your account or move money.
      </p>
    </div>
  );
}
