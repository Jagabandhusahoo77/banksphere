import { Link, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { MODULE_CATALOG } from "@/data/navigationCatalog";
import Badge from "@/components/common/Badge";
import Button from "@/components/common/Button";
import logo from "@/assets/branding/banksphere-logo.svg";

function navLinkClass(active: boolean) {
  return `flex items-center gap-2 px-3 py-2 rounded-md text-body-sm font-medium ${
    active ? "text-ink-primary bg-surface-muted" : "text-ink-secondary hover:bg-surface-muted"
  }`;
}

export default function AppLayout() {
  const { employee, hasPermission, logout } = useAuth();
  const location = useLocation();

  if (!employee) return null;

  const availableModules = MODULE_CATALOG.filter((entry) => hasPermission(entry.permission));
  const realNavItems = availableModules.filter((entry) => entry.route);
  const comingSoonItems = availableModules.filter((entry) => !entry.route);

  return (
    <div className="min-h-screen bg-surface-background">
      <header className="bg-white border-b border-surface-border">
        <div className="max-w-content mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <img src={logo} alt="BankSphere" className="h-7" />
            <span className="text-label text-ink-muted uppercase tracking-wide hidden sm:inline">Employee Portal</span>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <p className="text-body-sm font-medium text-ink-primary">
                {employee.firstName} {employee.lastName}
              </p>
              <p className="text-caption text-ink-muted">{employee.employeeNumber}</p>
            </div>
            <Button variant="outline" size="sm" icon="log-out" onClick={logout}>
              Log out
            </Button>
          </div>
        </div>
      </header>

      <div className="max-w-content mx-auto px-4 sm:px-6 py-6 grid grid-cols-1 md:grid-cols-[220px_1fr] gap-6">
        <nav aria-label="Main" className="space-y-6">
          <ul className="space-y-1">
            <li>
              <Link to="/profile" className={navLinkClass(location.pathname === "/profile")}>
                My Profile
              </Link>
            </li>
            {realNavItems.map((entry) => {
              const availableChildren = entry.children?.filter((child) => hasPermission(child.permission)) ?? [];
              return (
                <li key={entry.permission}>
                  <Link to={entry.route!} className={navLinkClass(location.pathname.startsWith(entry.route!))}>
                    {entry.label}
                  </Link>
                  {availableChildren.length > 0 && (
                    <ul className="ml-3 mt-1 space-y-1 border-l border-surface-border pl-3">
                      {availableChildren.map((child) => (
                        <li key={child.route}>
                          <Link
                            to={child.route!}
                            className={`block px-3 py-1.5 rounded-md text-body-sm ${
                              location.pathname === child.route
                                ? "text-brand-primary font-medium bg-brand-primary-light"
                                : "text-ink-secondary hover:bg-surface-muted"
                            }`}
                          >
                            {child.label}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>

          {comingSoonItems.length > 0 && (
            <div>
              <p className="px-3 text-label text-ink-muted uppercase tracking-wide mb-2">Your access</p>
              <ul className="space-y-1">
                {comingSoonItems.map((entry) => (
                  <li key={entry.permission} className="flex items-center justify-between gap-2 px-3 py-2 text-body-sm text-ink-secondary">
                    <span>{entry.label}</span>
                    <Badge tone="neutral">Coming soon</Badge>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </nav>

        <main>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
