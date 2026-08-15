import { Link } from "react-router-dom";
import Logo from "./Logo";
import Icon, { type IconName } from "@/components/common/Icon";

const PRODUCT_LINKS = [
  { to: "/savings-account", label: "Accounts" },
  { to: "/cards", label: "Cards" },
  { to: "/loans", label: "Loans" },
  { to: "/deposits", label: "Deposits" },
  { to: "/wealth", label: "Investments" },
];

const SERVICE_LINKS = [
  { to: "/#digital-banking-services", label: "Digital Banking" },
  { to: "/#digital-banking-services", label: "Payments" },
  { to: "/transfer", label: "Transfers" },
  { to: "/support", label: "Service Requests" },
];

const SUPPORT_LINKS = [
  { to: "/help", label: "Help Center" },
  { to: "/contact", label: "Contact" },
  { to: "/help", label: "FAQs" },
  { to: "/#security", label: "Security" },
];

const SOCIAL_PLACEHOLDERS: { label: string; icon: IconName }[] = [
  { label: "X (Twitter)", icon: "arrow-up-right" },
  { label: "LinkedIn", icon: "building" },
  { label: "Instagram", icon: "user" },
];

// Homepage in-page anchors (e.g. "/#digital-banking-services") work
// through a plain <Link> like any other route — Home.tsx handles the
// actual scroll-to-hash behavior once there (see its useEffect on
// location.hash), so no special-casing is needed here.
function FooterLink({ to, label }: { to: string; label: string }) {
  return (
    <Link to={to} className="text-body-sm hover:text-white transition-colors">
      {label}
    </Link>
  );
}

export default function Footer() {
  return (
    <footer className="bg-brand-primary-dark text-white/80">
      <div className="mx-auto max-w-content px-4 sm:px-6 py-section-sm">
        <div className="grid grid-cols-2 lg:grid-cols-6 gap-8">
          <div className="col-span-2">
            <Logo variant="white" className="h-7" />
            <p className="mt-4 text-body-sm max-w-xs">
              Digital banking built around your future — accounts, payments, cards, loans and
              investments in one secure experience.
            </p>
            <div className="mt-5 flex items-center gap-3">
              {SOCIAL_PLACEHOLDERS.map((social) => (
                <span
                  key={social.label}
                  title={`${social.label} (coming soon)`}
                  className="flex items-center justify-center w-9 h-9 rounded-full bg-white/10 text-white/70"
                >
                  <Icon name={social.icon} size={16} />
                </span>
              ))}
            </div>
          </div>

          <div>
            <h3 className="text-label uppercase tracking-wide text-white/50 mb-3">Products</h3>
            <ul className="space-y-2">
              {PRODUCT_LINKS.map((link) => (
                <li key={link.label}>
                  <FooterLink to={link.to} label={link.label} />
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3 className="text-label uppercase tracking-wide text-white/50 mb-3">Services</h3>
            <ul className="space-y-2">
              {SERVICE_LINKS.map((link) => (
                <li key={link.label}>
                  <FooterLink to={link.to} label={link.label} />
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3 className="text-label uppercase tracking-wide text-white/50 mb-3">Support</h3>
            <ul className="space-y-2">
              {SUPPORT_LINKS.map((link) => (
                <li key={link.label}>
                  <FooterLink to={link.to} label={link.label} />
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3 className="text-label uppercase tracking-wide text-white/50 mb-3">Company</h3>
            <ul className="space-y-2">
              <li>
                <FooterLink to="/about" label="About" />
              </li>
              <li className="text-body-sm text-white/40" title="Coming soon">
                Careers
              </li>
              <li className="text-body-sm text-white/40" title="Coming soon">
                Privacy policy
              </li>
              <li className="text-body-sm text-white/40" title="Coming soon">
                Terms of use
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-10 pt-6 border-t border-white/10 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <p className="text-caption text-white/50">
            &copy; {new Date().getFullYear()} BankSphere. A fictional educational banking platform —
            not a real bank, not affiliated with any real financial institution.
          </p>
        </div>
      </div>
    </footer>
  );
}
