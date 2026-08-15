import { Outlet } from "react-router-dom";
import PublicHeader from "@/components/navigation/PublicHeader";
import Footer from "@/components/navigation/Footer";

export default function PublicLayout() {
  return (
    <div className="min-h-screen flex flex-col">
      <PublicHeader />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
