import { useAuth } from "@/context/AuthContext";
import Logo from "./Logo";
import Icon from "@/components/common/Icon";

interface BankingHeaderProps {
  onMenuClick: () => void;
}

function timeOfDayGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "morning";
  if (hour < 17) return "afternoon";
  return "evening";
}

export default function BankingHeader({ onMenuClick }: BankingHeaderProps) {
  const { customer, logout } = useAuth();

  return (
    <header className="sticky top-0 z-30 bg-brand-primary-dark text-white">
      <div className="flex items-center justify-between px-4 sm:px-6 py-3 gap-3">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onMenuClick}
            aria-label="Open navigation menu"
            className="lg:hidden p-2 -ml-2 rounded-md hover:bg-white/10"
          >
            <Icon name="menu" size={22} />
          </button>
          <Logo variant="white" to="/dashboard" className="h-7" />
        </div>

        <div className="flex items-center gap-3">
          <span className="hidden sm:flex items-center gap-2 text-body-sm text-white/80">
            <Icon name="user" size={16} />
            <span className="max-w-[12rem] truncate">
              Good {timeOfDayGreeting()}, {customer?.firstName}
            </span>
          </span>
          <button
            type="button"
            onClick={logout}
            className="flex items-center gap-1.5 rounded-md px-3 py-2 text-body-sm font-medium hover:bg-white/10"
          >
            <Icon name="log-out" size={16} />
            <span className="hidden sm:inline">Log out</span>
          </button>
        </div>
      </div>
    </header>
  );
}
