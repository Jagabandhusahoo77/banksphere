import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";

/**
 * A route-level permission gate — nests inside ProtectedRoute (so
 * authentication is already established) and redirects to /unauthorized
 * if the signed-in employee's own permission set doesn't include the one
 * required. This is a UX convenience only: the backend enforces the same
 * (and the only authoritative) check via @PreAuthorize on every endpoint
 * this page would call, and would reject the request regardless of what
 * this component does or doesn't render — see ADR-006, Decision 4, and
 * AuthContext.hasPermission's own doc comment.
 */
export default function RequirePermission({ permission }: { permission: string }) {
  const { hasPermission } = useAuth();

  if (!hasPermission(permission)) {
    return <Navigate to="/unauthorized" replace />;
  }

  return <Outlet />;
}
