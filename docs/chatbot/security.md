# Chatbot Security Boundary

_Status: Phase 2 — the current mock chatbot has no security exposure because it has no capabilities beyond returning static text. This document exists to constrain what gets built **next**, before any of it exists._

## The current implementation cannot violate any of this

`chatService.ts` (see [architecture.md](architecture.md)) only ever returns a hardcoded string based on a keyword match. It has no network access, no access to `AuthContext`, no access to any BankSphere API, and no memory beyond the current browser tab's React state. There is nothing to secure yet — but the rules below are the constraint any future implementation must satisfy, written down now while the boundary is easy to state clearly.

## The chatbot must never directly execute

- Money transfers
- Withdrawals
- Deposits
- Beneficiary creation
- Password changes
- Card blocking/unblocking
- Loan approval
- Account modification of any kind

This holds even after a real LLM is integrated. A conversational interface is a poor place to put irreversible financial actions behind — natural language is ambiguous, LLMs can be manipulated by crafted input (prompt injection), and "the model decided to do it" is not an acceptable authorization model for moving money.

## Required future flow for anything action-like

```text
User
 ↓
Chatbot                    (understands what the user wants)
 ↓
Intent detection           (classifies: informational vs. actionable)
 ↓
Authenticated workflow     (routes to a real, existing screen/flow —
 ↓                          e.g. AccountDetails's deposit/withdraw form —
Explicit user confirmation  never performs the action itself)
 ↓
Banking API                (the actual mutation happens through the
                             same account-service/transaction-service
                             endpoints the UI already uses, with the
                             same validation — never a chatbot-only
                             code path)
```

In other words: the chatbot's job, even in a future real implementation, is to **understand and route**, not to **execute**. "Block my card" should end with the chatbot linking the user to a (future) card-management screen with the right context pre-filled, not with the chatbot calling a block-card API on the user's behalf mid-conversation.

## Data the chatbot must never send to a future LLM

- Passwords
- PINs
- CVVs
- Authentication tokens or session identifiers
- Full account numbers, where a masked/partial reference would do
- Any other customer information not necessary to answer the specific question asked

This applies to both what the frontend sends *to* a future Chat API, and what that service would be allowed to forward *to* an LLM provider. A RAG knowledge base (see [architecture.md](architecture.md)) built from product/FAQ content is fine to send; anything from `AuthContext` or a customer's real account/transaction data is not, unless a specific, deliberate design decision says otherwise — the default must be "don't send it."

## What's already consistent with this today

- `AuthContext`'s `customerId` is never read by `chatService.ts` or any chat component.
- The chatbot's canned responses for money-moving requests ("transfer money," "block my card," etc. — see `chatService.ts`'s `RULES`) explicitly tell the user it *can't* do that and redirects them to a real screen or Contact — this is the pattern above, already followed even in the mock.
- No API keys or credentials for any AI provider exist anywhere in this repository (verified as part of this phase's security scan — see the [engineering journal entry](../09-engineering-journal/)).
