import { NavLink } from "react-router-dom";
import Icon, { type IconName } from "@/components/common/Icon";

interface TabItem {
  to?: string;
  label: string;
  icon: IconName;
  onClick?: () => void;
}

interface MobileNavigationProps {
  onMoreClick: () => void;
}

/**
 * Fixed bottom tab bar for the four most common banking actions, plus a
 * "More" tab that opens the full BankingSidebar drawer for everything
 * else. Desktop uses the persistent BankingSidebar instead — this never
 * renders above the `lg` breakpoint.
 */
export default function MobileNavigation({ onMoreClick }: MobileNavigationProps) {
  const tabs: TabItem[] = [
    { to: "/dashboard", label: "Home", icon: "home" },
    { to: "/accounts", label: "Accounts", icon: "wallet" },
    { to: "/transactions", label: "History", icon: "list" },
    { to: "/payments", label: "Payments", icon: "arrow-up-right" },
    { label: "More", icon: "menu", onClick: onMoreClick },
  ];

  return (
    <nav
      aria-label="Banking, more options"
      className="lg:hidden fixed bottom-0 inset-x-0 z-30 bg-white border-t border-surface-border pb-[env(safe-area-inset-bottom)]"
    >
      <ul className="grid grid-cols-5">
        {tabs.map((tab) =>
          tab.to ? (
            <li key={tab.label}>
              <NavLink
                to={tab.to}
                className={({ isActive }) =>
                  `flex flex-col items-center justify-center gap-1 py-2.5 text-caption ${
                    isActive ? "text-brand-primary" : "text-ink-muted"
                  }`
                }
              >
                <Icon name={tab.icon} size={20} />
                {tab.label}
              </NavLink>
            </li>
          ) : (
            <li key={tab.label}>
              <button
                type="button"
                onClick={tab.onClick}
                className="w-full flex flex-col items-center justify-center gap-1 py-2.5 text-caption text-ink-muted"
              >
                <Icon name={tab.icon} size={20} />
                {tab.label}
              </button>
            </li>
          ),
        )}
      </ul>
    </nav>
  );
}
