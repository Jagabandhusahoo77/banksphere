import { useState, type FormEvent } from "react";
import Input from "@/components/common/Input";
import Button from "@/components/common/Button";
import Icon from "@/components/common/Icon";
import { useToast } from "@/components/common/Toast";

const CONTACT_POINTS = [
  { icon: "mail" as const, label: "Email", value: "support@banksphere.example" },
  { icon: "phone" as const, label: "Phone", value: "1800-123-4567 (toll-free)" },
  { icon: "clock" as const, label: "Hours", value: "Mon–Sat, 9:00 AM – 7:00 PM" },
];

export default function Contact() {
  const { showToast } = useToast();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    // BankSphere is a demo project with no messaging backend — this is an
    // honest client-side acknowledgment, not a simulated network call.
    showToast(
      "This is a demo project — your message wasn't actually sent, but this is exactly how it would work.",
      "info",
    );
    setName("");
    setEmail("");
    setMessage("");
  };

  return (
    <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm lg:py-section">
      <div className="max-w-2xl">
        <p className="text-label text-brand-primary uppercase tracking-wide">Contact</p>
        <h1 className="mt-2 text-h1 text-ink-primary">We're here to help</h1>
        <p className="mt-4 text-body text-ink-secondary">
          Have a question about opening an account or how BankSphere works? Reach out — or send a
          message below.
        </p>
      </div>

      <div className="mt-10 grid grid-cols-1 lg:grid-cols-5 gap-10">
        <div className="lg:col-span-2 space-y-6">
          {CONTACT_POINTS.map((point) => (
            <div key={point.label} className="flex items-start gap-4">
              <span className="flex items-center justify-center w-11 h-11 rounded-full bg-brand-primary-light text-brand-primary shrink-0">
                <Icon name={point.icon} size={20} />
              </span>
              <div>
                <p className="text-label text-ink-muted">{point.label}</p>
                <p className="text-body text-ink-primary">{point.value}</p>
              </div>
            </div>
          ))}
        </div>

        <form onSubmit={handleSubmit} className="lg:col-span-3 bg-white border border-surface-border rounded-lg p-6 space-y-5">
          <Input label="Full name" required value={name} onChange={(e) => setName(e.target.value)} />
          <Input
            label="Email address"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <div>
            <label htmlFor="contact-message" className="block text-label text-ink-secondary mb-1.5">
              Message
            </label>
            <textarea
              id="contact-message"
              required
              rows={5}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              className="w-full px-3.5 py-3 text-body text-ink-primary bg-white border border-surface-border rounded-md focus-visible:border-brand-primary"
            />
          </div>
          <Button type="submit" variant="primary" fullWidth>
            Send message
          </Button>
        </form>
      </div>
    </div>
  );
}
