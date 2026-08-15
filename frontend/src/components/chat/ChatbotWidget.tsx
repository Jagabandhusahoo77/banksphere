import { useState } from "react";
import { chatService, type ChatMessage } from "@/services/chatService";
import ChatWindow from "./ChatWindow";
import Icon from "@/components/common/Icon";

export default function ChatbotWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sending, setSending] = useState(false);

  const handleOpen = () => {
    setOpen(true);
    if (messages.length === 0) {
      setMessages([chatService.getGreeting()]);
    }
  };

  const handleSend = async (text: string) => {
    const userMessage = chatService.createUserMessage(text);
    setMessages((current) => [...current, userMessage]);
    setSending(true);

    const reply = await chatService.sendMessage(text);
    setMessages((current) => [...current, reply]);
    setSending(false);
  };

  return (
    <div className="fixed bottom-4 right-4 sm:bottom-6 sm:right-6 z-50 flex flex-col items-end gap-3">
      {open && <ChatWindow messages={messages} sending={sending} onSend={handleSend} onClose={() => setOpen(false)} />}

      {!open && (
        <button
          type="button"
          onClick={handleOpen}
          aria-label="Open BankSphere Assistant chat"
          className="flex items-center justify-center w-14 h-14 rounded-full bg-brand-primary text-white shadow-elevation-3 hover:bg-brand-primary-dark transition-colors"
        >
          <Icon name="headset" size={24} />
        </button>
      )}
    </div>
  );
}
