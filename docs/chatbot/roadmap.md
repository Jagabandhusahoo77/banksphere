# Chatbot Roadmap

_Status: Phase 2 delivered the mock implementation only (see [architecture.md](architecture.md)). Nothing below is scheduled to a specific phase yet — this is a suggested sequence, not a commitment._

## Phase 2 (done)

- Floating `ChatbotWidget`, `ChatWindow`, quick questions, typing indicator.
- `chatService.ts` mock: keyword-matched canned responses, no network calls.
- Security boundary documented ([security.md](security.md)) before any real capability exists, so future work has a constraint to build against rather than retrofitting one.

## Candidate next steps (not started, not scoped in detail)

1. **Real Chat API endpoint.** A `POST /api/v1/chat/messages` (or similar) on a backend service — likely a new small service rather than bolting it onto customer/account/transaction-service, to keep chat's request volume and failure modes isolated from banking operations. Would replace `chatService.ts`'s mock body with a real `fetch`/`axios` call; the component layer (`ChatWindow`, etc.) shouldn't need to change.
2. **LLM integration**, behind that Chat API — model choice, prompt design, and rate limiting are all undecided. Must not hold or forward any of the data listed as off-limits in [security.md](security.md).
3. **Knowledge retrieval / RAG** over BankSphere's actual product content — the `frontend/src/data/{cards,loans,deposits,faqs}.ts` files are a natural starting corpus, ideally synced to (or replaced by) a backend-owned knowledge source rather than duplicated.
4. **Intent detection + authenticated-workflow routing**, per the required flow in [security.md](security.md) — this is the piece that lets the chatbot be useful for things like "block my card" without ever being the thing that actually blocks it.
5. **Monitoring/auditing.** Once the chatbot can see authenticated context (even just "this user is signed in," not full account data), every chat session should be logged with enough detail to audit later — what was asked, what was answered, whether it triggered a routing/handoff — without logging the sensitive fields listed in [security.md](security.md). Not designed yet.

## Explicitly not happening yet

Per this phase's instructions: no real LLM, no external AI credentials, no `POST /api/v1/chat/messages` endpoint, no RAG implementation. All of the above are documented as direction, not as work in progress.
