import type { ChatMessage as ChatMessageType } from "@/services/chatService";

export default function ChatMessage({ message }: { message: ChatMessageType }) {
  const isUser = message.role === "user";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[85%] rounded-lg px-3.5 py-2.5 text-body-sm ${
          isUser ? "bg-brand-primary text-white rounded-br-sm" : "bg-surface-muted text-ink-primary rounded-bl-sm"
        }`}
      >
        {message.text}
      </div>
    </div>
  );
}
