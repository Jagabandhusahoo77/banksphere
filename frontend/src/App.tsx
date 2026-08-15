import { Route, Routes } from "react-router-dom";

import PublicLayout from "@/layouts/PublicLayout";
import AppLayout from "@/layouts/AppLayout";
import ProtectedRoute from "@/routes/ProtectedRoute";

import Home from "@/pages/public/Home";
import About from "@/pages/public/About";
import Contact from "@/pages/public/Contact";
import NotFound from "@/pages/public/NotFound";
import Login from "@/pages/auth/Login";
import Register from "@/pages/auth/Register";

import CardsCatalog from "@/pages/public/cards/CardsCatalog";
import CardDetail from "@/pages/public/cards/CardDetail";
import LoansCatalog from "@/pages/public/loans/LoansCatalog";
import LoanDetail from "@/pages/public/loans/LoanDetail";
import DepositsCatalog from "@/pages/public/deposits/DepositsCatalog";
import DepositDetail from "@/pages/public/deposits/DepositDetail";
import SavingsAccountDetail from "@/pages/public/accounts/SavingsAccountDetail";
import Help from "@/pages/public/Help";
import Business from "@/pages/public/Business";
import NRI from "@/pages/public/NRI";
import PremiumBanking from "@/pages/public/PremiumBanking";
import AccountTypes from "@/pages/public/AccountTypes";
import Wealth from "@/pages/public/Wealth";
import Insurance from "@/pages/public/Insurance";

import Dashboard from "@/pages/dashboard/Dashboard";
import Accounts from "@/pages/accounts/Accounts";
import AccountDetails from "@/pages/accounts/AccountDetails";
import Transactions from "@/pages/transactions/Transactions";
import Beneficiaries from "@/pages/beneficiaries/Beneficiaries";
import Kyc from "@/pages/kyc/Kyc";
import Cards from "@/pages/cards/Cards";
import Loans from "@/pages/loans/Loans";
import Transfer from "@/pages/transfer/Transfer";
import Payments from "@/pages/payments/Payments";
import Investments from "@/pages/investments/Investments";
import Profile from "@/pages/profile/Profile";
import Support from "@/pages/support/Support";
import ChatbotWidget from "@/components/chat/ChatbotWidget";

export default function App() {
  return (
    <>
      <Routes>
        {/* Public site */}
        <Route element={<PublicLayout />}>
          <Route path="/" element={<Home />} />
          <Route path="/about" element={<About />} />
          <Route path="/contact" element={<Contact />} />

          <Route path="/cards" element={<CardsCatalog />} />
          <Route path="/cards/:slug" element={<CardDetail />} />
          <Route path="/loans" element={<LoansCatalog />} />
          <Route path="/loans/:slug" element={<LoanDetail />} />
          <Route path="/deposits" element={<DepositsCatalog />} />
          <Route path="/deposits/:slug" element={<DepositDetail />} />
          <Route path="/savings-account" element={<SavingsAccountDetail />} />

          <Route path="/help" element={<Help />} />
          {/* Business/NRI/Premium Banking mega-menu targets, plus overflow
              from Personal's Accounts/Investments columns for products that
              don't have a dedicated catalog yet — every one is an honest
              ComingSoonPage, never a fabricated product page. See
              docs/frontend/routing.md. */}
          <Route path="/business" element={<Business />} />
          <Route path="/nri" element={<NRI />} />
          <Route path="/premium-banking" element={<PremiumBanking />} />
          <Route path="/account-types" element={<AccountTypes />} />
          <Route path="/wealth" element={<Wealth />} />
          <Route path="/insurance" element={<Insurance />} />
        </Route>

        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* Authenticated internet banking. /cards and /loans here are the
            "coming soon" placeholders for managing your own cards/loans —
            distinct from the public product-catalog pages above at the
            same-looking-but-unrelated /cards and /loans paths, so they're
            namespaced under /banking/* to avoid colliding with them. */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/accounts" element={<Accounts />} />
            <Route path="/accounts/:id" element={<AccountDetails />} />
            <Route path="/transactions" element={<Transactions />} />
            <Route path="/transfer" element={<Transfer />} />
            <Route path="/beneficiaries" element={<Beneficiaries />} />
            <Route path="/kyc" element={<Kyc />} />
            <Route path="/banking/cards" element={<Cards />} />
            <Route path="/banking/loans" element={<Loans />} />
            <Route path="/payments" element={<Payments />} />
            <Route path="/investments" element={<Investments />} />
            <Route path="/profile" element={<Profile />} />
            <Route path="/support" element={<Support />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>

      <ChatbotWidget />
    </>
  );
}
