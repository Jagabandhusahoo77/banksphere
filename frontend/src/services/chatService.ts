export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  text: string;
}

/**
 * Mock BankSphere Assistant. This is deliberately NOT connected to any
 * real LLM or backend — see docs/chatbot/architecture.md for the planned
 * future architecture (React → Chat API → Chatbot Service → LLM → RAG
 * over a BankSphere knowledge base), none of which exists yet.
 *
 * The response logic is a simple keyword match over a fixed set of
 * demo answers, isolated here so UI components never contain response
 * text directly and so this is the one place to swap in a real
 * `POST /api/v1/chat/messages` call later without touching the widget.
 *
 * Security boundary (see docs/chatbot/security.md): this service only
 * ever returns informational text. It has no access to — and must never
 * be given — the ability to move money, change account state, or see
 * authentication credentials. It doesn't call any BankSphere API today.
 */

interface Rule {
  keywords: string[];
  response: string;
}

const RULES: Rule[] = [
  {
    keywords: ["savings account", "current account", "what accounts", "types of account"],
    response:
      "BankSphere offers Savings and Current accounts today, plus Fixed and Recurring Deposits. You can see live balances for your own accounts once signed in — for general details, visit the Accounts and Deposits pages.",
  },
  {
    keywords: ["loan rate", "loan rates", "home loan", "personal loan", "car loan", "education loan"],
    response:
      "BankSphere's illustrative demo loan rates start from 8.50% p.a. for Home Loans, 9.00% p.a. for Education Loans, 9.25% p.a. for Car Loans, and 10.99% p.a. for Personal Loans. These are fictional demo rates for this portfolio project, not real market rates — see the Loans page for full details and a calculator.",
  },
  {
    keywords: ["fd rate", "fixed deposit", "deposit rate", "recurring deposit"],
    response:
      "BankSphere's illustrative demo Fixed Deposit rate is up to 7.25% p.a., and Recurring Deposits up to 6.75% p.a. — both fictional demo rates. See the Deposits page for tenure options and a returns calculator.",
  },
  {
    keywords: ["open an account", "open account", "sign up", "new customer", "become a customer"],
    response:
      "Self-service account opening isn't available in this phase of BankSphere. Please visit the Contact page and our team will help you get started.",
  },
  {
    keywords: ["contact support", "contact us", "talk to someone", "human", "phone number", "email"],
    response:
      "You can reach BankSphere support via the Contact page — it has our email, phone number, and hours. I can only share general information here; I can't access your account.",
  },
  {
    keywords: ["block my card", "block card", "lost card", "stolen card", "freeze card"],
    response:
      "For your security, I can't block, freeze, or otherwise modify a card from this chat. Card management isn't implemented yet in BankSphere — please use the Contact page for anything urgent.",
  },
  {
    keywords: ["transfer money", "send money", "withdraw", "deposit money", "pay bill"],
    response:
      "I can't move money, make withdrawals or deposits, or change anything on your account — I only provide information. Deposits and withdrawals are available today from an account's details page once you're signed in.",
  },
];

const FALLBACK_RESPONSE =
  "I can help with general information about BankSphere's accounts, cards, loans, and deposits — try one of the quick questions below, or ask about a specific product. For anything account-specific, please sign in or visit the Contact page.";

function matchResponse(userText: string): string {
  const normalized = userText.toLowerCase();
  const match = RULES.find((rule) => rule.keywords.some((keyword) => normalized.includes(keyword)));
  return match ? match.response : FALLBACK_RESPONSE;
}

let messageCounter = 0;
function nextId(): string {
  messageCounter += 1;
  return `msg-${messageCounter}-${Date.now()}`;
}

export const chatService = {
  /**
   * Simulates the round-trip a real `POST /api/v1/chat/messages` call
   * would make (a short delay), then resolves with a canned response.
   * Never throws for demo purposes — a real integration would need real
   * error handling here.
   */
  async sendMessage(userText: string): Promise<ChatMessage> {
    const responseText = matchResponse(userText);
    const delayMs = 500 + Math.random() * 400;

    await new Promise((resolve) => setTimeout(resolve, delayMs));

    return {
      id: nextId(),
      role: "assistant",
      text: responseText,
    };
  },

  createUserMessage(text: string): ChatMessage {
    return { id: nextId(), role: "user", text };
  },

  getGreeting(): ChatMessage {
    return {
      id: nextId(),
      role: "assistant",
      text: "Hello! 👋 How can I help you today?",
    };
  },
};
