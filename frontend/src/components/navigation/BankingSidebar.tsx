import { NavLink } from "react-router-dom";
import Icon, { type IconName } from "@/components/common/Icon";

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
}

const NAV_ITEMS: NavItem[] = [
  { to: "/dashboard", label: "Dashboard", icon: "home" },
  { to: "/accounts", label: "Accounts", icon: "wallet" },
  { to: "/transfer", label: "Transfer", icon: "arrow-right" },
  { to: "/beneficiaries", label: "Beneficiaries", icon: "users" },
  { to: "/transactions", label: "Transactions", icon: "list" },
  { to: "/kyc", label: "KYC", icon: "shield-check" },
  { to: "/banking/cards", label: "Cards", icon: "credit-card" },
  { to: "/banking/loans", label: "Loans", icon: "building" },
  { to: "/payments", label: "Payments", icon: "arrow-up-right" },
  { to: "/investments", label: "Investments", icon: "trending-up" },
  { to: "/profile", label: "Profile", icon: "user" },
  { to: "/support", label: "Support", icon: "headset" },
];

interface BankingSidebarProps {
  mobileOpen: boolean;
  onClose: () => void;
}

function NavList({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav className="flex flex-col gap-1 px-3" aria-label="Banking">
      {NAV_ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          onClick={onNavigate}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-md px-3 py-2.5 text-body-sm font-medium transition-colors ${
              isActive ? "bg-brand-primary-light text-brand-primary" : "text-ink-secondary hover:bg-surface-muted"
            }`
          }
        >
          <Icon name={item.icon} size={18} />
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

export default function BankingSidebar({ mobileOpen, onClose }: BankingSidebarProps) {
  return (
    <>
      {/* Desktop: persistent sidebar */}
      <aside className="hidden lg:block w-64 shrink-0 border-r border-surface-border bg-white py-6">
        <NavList />
      </aside>

      {/* Mobile: slide-in drawer */}
      <div className={`lg:hidden fixed inset-0 z-40 ${mobileOpen ? "" : "pointer-events-none"}`}>
        <div
          className={`absolute inset-0 bg-ink-primary/50 transition-opacity ${mobileOpen ? "opacity-100" : "opacity-0"}`}
          onClick={onClose}
          aria-hidden="true"
        />
        <aside
          className={`absolute inset-y-0 left-0 w-72 bg-white shadow-elevation-4 py-6 transition-transform duration-200 ${
            mobileOpen ? "translate-x-0" : "-translate-x-full"
          }`}
          aria-hidden={!mobileOpen}
        >
          <NavList onNavigate={onClose} />
        </aside>
      </div>
    </>
  );
}
