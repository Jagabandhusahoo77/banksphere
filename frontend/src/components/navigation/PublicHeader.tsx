import { useEffect, useRef, useState } from "react";
import { NavLink, Link, useLocation } from "react-router-dom";
import Logo from "./Logo";
import MegaMenuPanel from "./MegaMenuPanel";
import PublicNavDrawer from "./PublicNavDrawer";
import Button from "@/components/common/Button";
import Icon from "@/components/common/Icon";
import { PRIMARY_NAV } from "@/data/navigation";

const CLOSE_DELAY_MS = 150;

/**
 * The header uses `position: sticky` inside normal document flow (see
 * PublicLayout.tsx) — it reserves its own space at the top of the page
 * rather than floating over content, so it cannot overlap the hero
 * section below it. A `position: sticky` element already establishes a
 * containing block for `position: absolute` descendants (same as
 * `relative` would), so the mega menu panel (`absolute top-full` on
 * MegaMenuPanel) positions against the header's own box without an extra
 * `relative` class, which would be redundant with `sticky` on the same
 * element. Verified by inspection since no browser
 * was available in this environment — see the Phase 2 engineering
 * journal, and docs/frontend/homepage-design.md for this redesign.
 */
export default function PublicHeader() {
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const navRegionRef = useRef<HTMLDivElement>(null);
  const location = useLocation();

  // Close any open menu/drawer on route change, so a mega-menu link click
  // doesn't leave a stale panel open on the next page.
  useEffect(() => {
    setOpenMenu(null);
    setDrawerOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpenMenu(null);
    };
    const handleOutsideClick = (event: MouseEvent) => {
      if (navRegionRef.current && !navRegionRef.current.contains(event.target as Node)) {
        setOpenMenu(null);
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("mousedown", handleOutsideClick);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("mousedown", handleOutsideClick);
    };
  }, []);

  const cancelClose = () => {
    if (closeTimer.current) {
      clearTimeout(closeTimer.current);
      closeTimer.current = null;
    }
  };

  const scheduleClose = () => {
    cancelClose();
    closeTimer.current = setTimeout(() => setOpenMenu(null), CLOSE_DELAY_MS);
  };

  const activeItem = PRIMARY_NAV.find((item) => item.label === openMenu);

  return (
    <header className="sticky top-0 z-40 bg-white/95 backdrop-blur border-b border-surface-border">
      <div className="mx-auto max-w-content px-4 sm:px-6 flex items-center justify-between py-3.5">
        <Logo className="h-8" />

        <div
          ref={navRegionRef}
          className="hidden lg:flex items-center gap-1"
          onMouseLeave={scheduleClose}
          onMouseEnter={cancelClose}
        >
          <nav className="flex items-center gap-1" aria-label="Primary">
            {PRIMARY_NAV.map((item) =>
              item.megaMenu ? (
                <button
                  key={item.label}
                  type="button"
                  aria-haspopup="true"
                  aria-expanded={openMenu === item.label}
                  onMouseEnter={() => setOpenMenu(item.label)}
                  onClick={() => setOpenMenu((current) => (current === item.label ? null : item.label))}
                  className={`flex items-center gap-1 rounded-md px-3 py-2 text-body-sm font-medium transition-colors ${
                    openMenu === item.label ? "text-brand-primary" : "text-ink-secondary hover:text-brand-primary"
                  }`}
                >
                  {item.label}
                  <Icon
                    name="chevron-down"
                    size={14}
                    className={`transition-transform ${openMenu === item.label ? "rotate-180" : ""}`}
                  />
                </button>
              ) : (
                <NavLink
                  key={item.label}
                  to={item.to ?? "/"}
                  className={({ isActive }) =>
                    `rounded-md px-3 py-2 text-body-sm font-medium transition-colors ${
                      isActive ? "text-brand-primary" : "text-ink-secondary hover:text-brand-primary"
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ),
            )}
          </nav>

          {activeItem?.megaMenu && (
            <MegaMenuPanel columns={activeItem.megaMenu} onNavigate={() => setOpenMenu(null)} />
          )}
        </div>

        <div className="hidden lg:flex items-center gap-3">
          <Link to="/login">
            <Button variant="outline" size="sm">
              Log in
            </Button>
          </Link>
          <Link to="/contact">
            <Button variant="primary" size="sm" icon="arrow-right" iconPosition="right" className="shadow-elevation-1">
              Open an Account
            </Button>
          </Link>
        </div>

        <button
          type="button"
          className="lg:hidden text-ink-primary p-2 -mr-2"
          aria-label="Open menu"
          aria-expanded={drawerOpen}
          onClick={() => setDrawerOpen(true)}
        >
          <Icon name="menu" size={24} />
        </button>
      </div>

      <PublicNavDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} items={PRIMARY_NAV} />
    </header>
  );
}
