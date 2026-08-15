# Chatbot Architecture

_Status: Phase 2 — mock implementation only. No LLM, no backend endpoint, no external API calls of any kind._

## Current implementation (what actually exists)

```text
ChatbotWidget.tsx (floating button, bottom-right, mounted globally in App.tsx)
        │
        ▼
ChatWindow.tsx (message list, quick questions, input — components/chat/)
        │  onSend(text)
        ▼
chatService.ts (frontend/src/services/chatService.ts)
        │  keyword match against a fixed Rule[] list
        │  ~500-900ms artificial delay (setTimeout), simulating a round trip
        ▼
Canned response string, returned as a ChatMessage
```

Everything above runs **entirely in the browser**. There is no `POST /api/v1/chat/messages` call, no `chat-service` backend, and no LLM API key anywhere in this codebase — `chatService.sendMessage()` never leaves the client.

**Response logic:** `chatService.ts` holds a small `Rule[]` array — each rule is a list of keywords and a canned response string. `matchResponse()` lowercases the user's input and returns the first rule whose keywords appear as a substring; if nothing matches, a fixed fallback response is returned. This is intentionally simple — a real intent-classification system is out of scope for this phase.

**Why isolated in `chatService.ts` and not inline in the components:** so that swapping the mock for a real `POST /api/v1/chat/messages` call later is a one-file change — `ChatbotWidget.tsx` and `ChatWindow.tsx` only know about `chatService.sendMessage(text): Promise<ChatMessage>`, not about how the response is produced.

## Component breakdown

| Component | Responsibility |
|---|---|
| `ChatbotWidget.tsx` | Owns all chat state (`messages`, `sending`, `open`). Renders the floating button when closed, `ChatWindow` when open. |
| `ChatWindow.tsx` | The panel: header (BankSphere logo + close), scrollable message list, quick questions, input bar. Handles Escape-to-close and auto-scroll to the newest message. |
| `ChatMessage.tsx` | A single message bubble, styled differently for `user` vs `assistant`. |
| `ChatInput.tsx` | Controlled text field + send button; disabled while a response is pending. |
| `QuickQuestions.tsx` | The six suggested-question chips (see below); clicking one sends it exactly like typing it. |
| `TypingIndicator.tsx` | Three-dot animation shown while `sending` is true. |

## Quick questions

Fixed list in `QuickQuestions.tsx`, matching the six topics `chatService.ts` has rules for:

1. What savings accounts are available?
2. What are the loan rates?
3. What is the FD rate?
4. How do I open an account?
5. How do I contact support?
6. How do I block my card?

## Future architecture (not implemented)

```text
React Web
     ↓
Chat API                    ← does not exist. Would be a new endpoint,
     ↓                         likely POST /api/v1/chat/messages, on a
Chatbot Service                new or existing backend service.
     ↓
LLM                         ← no model chosen, no API key, no
     ↓                         credentials of any kind exist in this repo.
Knowledge Retrieval / RAG
     ↓
BankSphere Knowledge Base
```

**Potential knowledge sources for a future RAG layer** (not built): product information (cards/loans/deposits — the same data already structured in `frontend/src/data/`), FAQs (`frontend/src/data/faqs.ts`), account help, card help, loan information, FD information, security guidance, support procedures.

None of this — the Chat API, the Chatbot Service, the LLM integration, or the RAG/knowledge-base layer — is implemented in this phase. See [security.md](security.md) for the authentication/authorization boundary that would need to exist before any of it could safely act on a real account, and [roadmap.md](roadmap.md) for suggested sequencing.
