const QUESTIONS = [
  "What savings accounts are available?",
  "What are the loan rates?",
  "What is the FD rate?",
  "How do I open an account?",
  "How do I contact support?",
  "How do I block my card?",
];

interface QuickQuestionsProps {
  onSelect: (question: string) => void;
  disabled?: boolean;
}

export default function QuickQuestions({ onSelect, disabled }: QuickQuestionsProps) {
  return (
    <div className="flex flex-wrap gap-2 px-4 py-3 border-t border-surface-border">
      {QUESTIONS.map((question) => (
        <button
          key={question}
          type="button"
          disabled={disabled}
          onClick={() => onSelect(question)}
          className="text-caption px-2.5 py-1.5 rounded-pill border border-surface-border text-ink-secondary hover:border-brand-primary hover:text-brand-primary disabled:opacity-50 disabled:pointer-events-none"
        >
          {question}
        </button>
      ))}
    </div>
  );
}
