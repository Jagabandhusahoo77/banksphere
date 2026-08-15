import { useState } from "react";
import { Outlet } from "react-router-dom";
import BankingHeader from "@/components/navigation/BankingHeader";
import BankingSidebar from "@/components/navigation/BankingSidebar";
import MobileNavigation from "@/components/navigation/MobileNavigation";

export default function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen flex flex-col bg-surface-background">
      <BankingHeader onMenuClick={() => setSidebarOpen(true)} />

      <div className="flex flex-1">
        <BankingSidebar mobileOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

        <main className="flex-1 min-w-0 px-4 sm:px-6 py-6 pb-20 lg:pb-6">
          <div className="mx-auto w-full max-w-content">
            <Outlet />
          </div>
        </main>
      </div>

      <MobileNavigation onMoreClick={() => setSidebarOpen(true)} />
    </div>
  );
}
