import { Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "@/routes/ProtectedRoute";
import RequirePermission from "@/routes/RequirePermission";
import AppLayout from "@/layouts/AppLayout";
import Login from "@/pages/auth/Login";
import Profile from "@/pages/profile/Profile";
import CashOperations from "@/pages/cash-operations/CashOperations";
import CashDeposit from "@/pages/cash-operations/CashDeposit";
import CashDepositHistory from "@/pages/cash-operations/CashDepositHistory";
import Customer360Search from "@/pages/customers/Customer360Search";
import Customer360 from "@/pages/customers/Customer360";
import KycQueue from "@/pages/kyc/KycQueue";
import KycUnderReview from "@/pages/kyc/KycUnderReview";
import KycCompleted from "@/pages/kyc/KycCompleted";
import KycReviewApplication from "@/pages/kyc/KycReviewApplication";
import Unauthorized from "@/pages/errors/Unauthorized";
import NotFound from "@/pages/errors/NotFound";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/unauthorized" element={<Unauthorized />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/profile" replace />} />
          <Route path="/profile" element={<Profile />} />

          {/* CASH_DEPOSIT-gated — backend @PreAuthorize is authoritative;
              this only spares an employee who lacks it from a confusing
              403 mid-flow. See RequirePermission's own doc comment. */}
          <Route element={<RequirePermission permission="CASH_DEPOSIT" />}>
            <Route path="/cash-operations" element={<CashOperations />} />
            <Route path="/cash-operations/deposit" element={<CashDeposit />} />
            <Route path="/cash-operations/history" element={<CashDepositHistory />} />
          </Route>

          {/* Customer 360 — CUSTOMER_VIEW is the floor permission; the
              backend's own section-level graceful degradation decides
              what's actually populated per caller. See ADR-008. */}
          <Route element={<RequirePermission permission="CUSTOMER_VIEW" />}>
            <Route path="/customers" element={<Customer360Search />} />
            <Route path="/customers/:customerId/360" element={<Customer360 />} />
          </Route>

          {/* KYC & Compliance — KYC_VIEW is the floor permission; the
              review screen itself further gates individual actions
              (verify/reject document, request-information, approve,
              reject) on KYC_REVIEW/KYC_APPROVE/KYC_REJECT via
              useAuth().hasPermission, backed by the same @PreAuthorize
              checks on kyc-service. See ADR-008. */}
          <Route element={<RequirePermission permission="KYC_VIEW" />}>
            <Route path="/kyc/queue" element={<KycQueue />} />
            <Route path="/kyc/under-review" element={<KycUnderReview />} />
            <Route path="/kyc/completed" element={<KycCompleted />} />
            <Route path="/kyc/applications/:id" element={<KycReviewApplication />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
