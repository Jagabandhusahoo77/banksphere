import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import Button from "@/components/common/Button";
import Input from "@/components/common/Input";
import Card from "@/components/common/Card";
import logo from "@/assets/branding/banksphere-logo.svg";

export default function Login() {
  const { isAuthenticated, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) {
    const from = (location.state as { from?: string } | null)?.from ?? "/profile";
    return <Navigate to={from} replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    setSubmitting(true);
    setError(null);
    try {
      await login(username, password);
      const from = (location.state as { from?: string } | null)?.from ?? "/profile";
      navigate(from, { replace: true });
    } catch (err) {
      // The backend deliberately never distinguishes "unknown username"
      // from "wrong password" from "inactive"/"locked" in this response —
      // see employee-service's InvalidCredentialsException. This screen
      // must not try to guess or embellish beyond that generic message.
      setError(getFriendlyErrorMessage(err, { 401: "Invalid username or password." }));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-background flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="flex flex-col items-center mb-6">
          <img src={logo} alt="BankSphere" className="h-9 mb-3" />
          <p className="text-body-sm text-ink-muted">Employee Portal</p>
        </div>

        <Card>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <h1 className="text-h3 text-ink-primary mb-1">Sign in</h1>
            <p className="text-body-sm text-ink-secondary mb-4">
              Access is restricted to authorized BankSphere staff. Contact your administrator if you need an account.
            </p>

            <Input
              label="Username"
              name="username"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              required
            />
            <Input
              label="Password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />

            {error && (
              <p role="alert" className="text-body-sm text-semantic-error bg-semantic-error-light rounded-md px-3 py-2">
                {error}
              </p>
            )}

            <Button type="submit" fullWidth loading={submitting} disabled={!username || !password}>
              Sign in
            </Button>
          </form>
        </Card>
      </div>
    </div>
  );
}
