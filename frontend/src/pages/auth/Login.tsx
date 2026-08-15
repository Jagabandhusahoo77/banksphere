import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import Logo from "@/components/navigation/Logo";
import Input from "@/components/common/Input";
import Button from "@/components/common/Button";
import Icon from "@/components/common/Icon";
import DevOtpInboxPanel from "@/components/common/DevOtpInboxPanel";
import { getFriendlyErrorMessage } from "@/utils/apiError";
import securityIllustration from "@/assets/illustrations/security/security-shield.svg";

type LoginMode = "password" | "otp";
type OtpStep = "identifier" | "code";

export default function Login() {
  const { isAuthenticated, login, requestOtp, verifyOtp } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [mode, setMode] = useState<LoginMode>("password");

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Phase 9D — OTP login state, independent of the password form above.
  const [otpStep, setOtpStep] = useState<OtpStep>("identifier");
  const [identifier, setIdentifier] = useState("");
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [otp, setOtp] = useState("");
  const [otpInfo, setOtpInfo] = useState<string | null>(null);

  if (isAuthenticated) {
    const redirectTo = (location.state as { from?: string } | null)?.from ?? "/dashboard";
    return <Navigate to={redirectTo} replace />;
  }

  const justRegistered = Boolean((location.state as { registered?: boolean } | null)?.registered);
  const redirectAfterLogin = () => {
    const redirectTo = (location.state as { from?: string } | null)?.from ?? "/dashboard";
    navigate(redirectTo, { replace: true });
  };

  const switchMode = (next: LoginMode) => {
    setMode(next);
    setError(null);
    setOtpStep("identifier");
    setChallengeId(null);
    setOtp("");
    setOtpInfo(null);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    if (!email.trim() || !password) {
      setError("Enter your email and password to continue.");
      return;
    }

    setSubmitting(true);
    try {
      await login(email.trim(), password);
      redirectAfterLogin();
    } catch {
      // Generic on purpose — never reveals whether the email exists.
      setError("Incorrect email or password. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleRequestOtp = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    if (!identifier.trim()) {
      setError("Enter the email or phone number on your account.");
      return;
    }

    setSubmitting(true);
    try {
      const { challengeId: newChallengeId } = await requestOtp(identifier.trim());
      setChallengeId(newChallengeId);
      setOtpStep("code");
      // Same generic copy regardless of whether the identifier matched a
      // real customer — see AuthContext.requestOtp's own javadoc.
      setOtpInfo("If that email or phone is registered with BankSphere, a 6-digit code has been sent.");
    } catch (err) {
      setError(getFriendlyErrorMessage(err, { 429: "Too many attempts. Please wait a moment before trying again." }));
    } finally {
      setSubmitting(false);
    }
  };

  const handleVerifyOtp = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    if (!challengeId || otp.trim().length < 4) {
      setError("Enter the 6-digit code we sent you.");
      return;
    }

    setSubmitting(true);
    try {
      await verifyOtp(challengeId, otp.trim());
      redirectAfterLogin();
    } catch (err) {
      setError(getFriendlyErrorMessage(err, { 400: "That code is incorrect or has expired. Please try again." }));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen grid grid-cols-1 lg:grid-cols-2">
      {/* Brand panel */}
      <div className="hidden lg:flex flex-col justify-between bg-brand-primary-dark px-12 py-10">
        <Logo variant="white" to="/" />
        <div className="max-w-sm">
          <img src={securityIllustration} alt="" className="w-40 h-auto mb-6" />
          <h2 className="text-h2 text-white">Secure digital banking, built around you.</h2>
          <p className="mt-3 text-body-sm text-white/70">
            Every session is verified before your account data ever loads, and every deposit or
            withdrawal is recorded to an auditable transaction ledger.
          </p>
        </div>
        <p className="text-caption text-white/40">
          &copy; {new Date().getFullYear()} BankSphere — a fictional educational banking platform.
        </p>
      </div>

      {/* Form panel */}
      <div className="flex flex-col justify-center px-6 py-12 sm:px-12">
        <div className="w-full max-w-sm mx-auto">
          <div className="lg:hidden mb-8">
            <Logo />
          </div>

          <h1 className="text-h1 text-ink-primary">Welcome back</h1>
          <p className="mt-2 text-body-sm text-ink-secondary">Sign in to access your BankSphere account.</p>

          {justRegistered && (
            <div className="mt-4 flex items-center gap-2 rounded-md bg-semantic-success/10 px-3.5 py-3 text-body-sm text-semantic-success">
              <Icon name="check-circle" size={18} />
              Account created. Sign in to continue.
            </div>
          )}

          <div className="mt-6 flex gap-2" role="tablist" aria-label="Sign-in method">
            {(
              [
                ["password", "Password"],
                ["otp", "One-time code"],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                role="tab"
                aria-selected={mode === value}
                onClick={() => switchMode(value)}
                className={`flex-1 rounded-md py-2 text-caption sm:text-body-sm font-medium transition-colors ${
                  mode === value ? "bg-brand-primary text-white" : "bg-surface-muted text-ink-secondary"
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          {mode === "password" && (
            <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-5">
              <Input
                label="Email address"
                id="email"
                type="email"
                autoComplete="email"
                autoFocus
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
              />

              <div>
                <label htmlFor="password" className="block text-label text-ink-secondary mb-1.5">
                  Password
                </label>
                <div className="relative">
                  <input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    aria-invalid={Boolean(error) || undefined}
                    className={`w-full h-11 px-3.5 pr-11 text-body text-ink-primary bg-white border rounded-md transition-colors placeholder:text-ink-muted ${
                      error
                        ? "border-semantic-error focus-visible:ring-semantic-error"
                        : "border-surface-border focus-visible:border-brand-primary"
                    }`}
                    placeholder="••••••••"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute inset-y-0 right-0 flex items-center px-3 text-ink-muted hover:text-ink-secondary"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    <Icon name={showPassword ? "eye-off" : "eye"} size={18} />
                  </button>
                </div>
                {error && (
                  <p className="mt-1.5 text-caption text-semantic-error" role="alert">
                    {error}
                  </p>
                )}
              </div>

              <Button type="submit" variant="primary" size="lg" fullWidth loading={submitting}>
                {submitting ? "Signing in…" : "Sign in"}
              </Button>
            </form>
          )}

          {mode === "otp" && otpStep === "identifier" && (
            <form onSubmit={handleRequestOtp} noValidate className="mt-6 space-y-5">
              <Input
                label="Email or phone number"
                id="identifier"
                autoComplete="username"
                autoFocus
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                placeholder="you@example.com"
                error={error ?? undefined}
              />
              <Button type="submit" variant="primary" size="lg" fullWidth loading={submitting}>
                {submitting ? "Sending code…" : "Send one-time code"}
              </Button>
            </form>
          )}

          {mode === "otp" && otpStep === "code" && (
            <form onSubmit={handleVerifyOtp} noValidate className="mt-6 space-y-5">
              {otpInfo && (
                <p className="text-body-sm text-ink-secondary bg-surface-muted rounded-md px-3.5 py-3">{otpInfo}</p>
              )}
              <Input
                label="6-digit code"
                id="otp"
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 8))}
                placeholder="123456"
                error={error ?? undefined}
              />
              <Button type="submit" variant="primary" size="lg" fullWidth loading={submitting}>
                {submitting ? "Verifying…" : "Verify & sign in"}
              </Button>
              <button
                type="button"
                onClick={() => {
                  setOtpStep("identifier");
                  setChallengeId(null);
                  setOtp("");
                  setError(null);
                }}
                className="w-full text-center text-body-sm text-brand-primary hover:underline"
              >
                Use a different email or phone
              </button>
              <DevOtpInboxPanel onSelectOtp={setOtp} />
            </form>
          )}

          <div className="mt-6 flex items-center gap-2 text-caption text-ink-muted">
            <Icon name="shield-check" size={16} />
            Secure digital banking — your session is verified before any account data loads.
          </div>

          <p className="mt-8 text-caption text-ink-muted border-t border-surface-border pt-6">
            New to BankSphere?{" "}
            <Link to="/register" className="text-brand-primary hover:underline">
              Create an account
            </Link>
            .
          </p>
        </div>
      </div>
    </div>
  );
}
