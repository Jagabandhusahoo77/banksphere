import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import type { PrimaryNavItem } from "@/data/navigation";
import Button from "@/components/common/Button";
import Icon from "@/components/common/Icon";

interface PublicNavDrawerProps {
  open: boolean;
  onClose: () => void;
  items: PrimaryNavItem[];
}

/**
 * Mobile navigation drawer — a proper redesign of the mega menu for small
 * screens, not a shrunk version of the desktop layout. Each mega-menu item
 * becomes a single-level disclosure accordion (same expand/collapse idiom
 * as FAQAccordion.tsx) flattening its columns into one scrollable list;
 * two levels of nested disclosure would be worse on mobile than one flat
 * list per top-level item. Visual language matches BankingSidebar.tsx's
 * existing mobile drawer (transform + backdrop) so the app's two "drawer"
 * patterns feel related.
 */
export default function PublicNavDrawer({ open, onClose, items }: PublicNavDrawerProps) {
  const [expanded, setExpanded] = useState<string | null>(null);

  return (
    <div className={`lg:hidden fixed inset-0 z-50 ${open ? "" : "pointer-events-none"}`}>
      <div
        className={`absolute inset-0 bg-ink-primary/50 transition-opacity ${open ? "opacity-100" : "opacity-0"}`}
        onClick={onClose}
        aria-hidden="true"
      />
      <aside
        className={`absolute inset-y-0 right-0 w-full max-w-sm bg-white shadow-elevation-4 flex flex-col transition-transform duration-200 ${
          open ? "translate-x-0" : "translate-x-full"
        }`}
        aria-hidden={!open}
      >
        <div className="flex items-center justify-between px-4 py-4 border-b border-surface-border">
          <span className="text-label text-ink-muted uppercase tracking-wide">Menu</span>
          <button type="button" onClick={onClose} aria-label="Close menu" className="text-ink-primary p-2 -mr-2">
            <Icon name="close" size={22} />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-4 py-2" aria-label="Primary">
          {items.map((item) =>
            item.megaMenu ? (
              <div key={item.label} className="border-b border-surface-border last:border-0">
                <button
                  type="button"
                  onClick={() => setExpanded((current) => (current === item.label ? null : item.label))}
                  aria-expanded={expanded === item.label}
                  className="w-full flex items-center justify-between py-3 text-body font-medium text-ink-primary"
                >
                  {item.label}
                  <Icon
                    name="chevron-down"
                    size={18}
                    className={`transition-transform ${expanded === item.label ? "rotate-180" : ""}`}
                  />
                </button>
                {expanded === item.label && (
                  <div className="pb-3 space-y-3">
                    {item.megaMenu.map((column) => (
                      <div key={column.heading}>
                        <p className="text-label text-ink-muted uppercase tracking-wide mb-1.5">{column.heading}</p>
                        <ul className="space-y-0.5">
                          {column.items.map((link) => (
                            <li key={link.label}>
                              <Link
                                to={link.to}
                                onClick={onClose}
                                className="block rounded-md px-2 py-2 text-body-sm text-ink-secondary hover:bg-surface-muted"
                              >
                                {link.label}
                              </Link>
                            </li>
                          ))}
                        </ul>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <NavLink
                key={item.label}
                to={item.to ?? "/"}
                onClick={onClose}
                className={({ isActive }) =>
                  `block py-3 text-body font-medium border-b border-surface-border last:border-0 ${
                    isActive ? "text-brand-primary" : "text-ink-primary"
                  }`
                }
              >
                {item.label}
              </NavLink>
            ),
          )}
        </nav>

        <div className="px-4 py-4 border-t border-surface-border flex flex-col gap-2">
          <Link to="/login" onClick={onClose}>
            <Button variant="outline" size="md" fullWidth>
              Log in
            </Button>
          </Link>
          <Link to="/contact" onClick={onClose}>
            <Button variant="primary" size="md" fullWidth icon="arrow-right" iconPosition="right">
              Open an Account
            </Button>
          </Link>
        </div>
      </aside>
    </div>
  );
}
