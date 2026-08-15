import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import Logo from "@/components/navigation/Logo";
import Input from "@/components/common/Input";
import Button from "@/components/common/Button";
import Icon from "@/components/common/Icon";
import securityIllustration from "@/assets/illustrations/security/security-shield.svg";

// Mirrors the backend's password policy (see customer-service's
// RegisterRequest validation) — checked client-side purely for a faster
// feedback loop; the server re-validates regardless.
const PASSWORD_MIN_LENGTH = 8;

function passwordMeetsPolicy(password: string): boolean {
  return password.length >= PASSWORD_MIN_LENGTH && /[A-Za-z]/.test(password) && /\d/.test(password);
}

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [address, setAddress] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    if (!firstName.trim() || !lastName.trim() || !email.trim() || !phone.trim() || !dateOfBirth || !address.trim()) {
      setError("Please fill in every field to continue.");
      return;
    }
    if (!passwordMeetsPolicy(password)) {
      setError("Password must be at least 8 characters and include a letter and a number.");
      return;
    }
    if (password !== confirmPassword) {
      setError("Passwords don't match.");
      return;
    }

    setSubmitting(true);
    try {
      await register({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
        phone: phone.trim(),
        dateOfBirth,
        address: address.trim(),
        password,
      });
      navigate("/login", { state: { registered: true } });
    } catch (err) {
      setError(err instanceof Error ? err.message : "We couldn't create your account. Please try again.");
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
          <h2 className="text-h2 text-white">Open your BankSphere account in minutes.</h2>
          <p className="mt-3 text-body-sm text-white/70">
            Your password is never stored in plain text, and every session is backed by a signed,
            expiring access token.
          </p>
        </div>
        <p className="text-caption text-white/40">
          &copy; {new Date().getFullYear()} BankSphere — a fictional educational banking platform.
        </p>
      </div>

      {/* Form panel */}
      <div className="flex flex-col justify-center px-6 py-12 sm:px-12">
        <div className="w-full max-w-md mx-auto">
          <div className="lg:hidden mb-8">
            <Logo />
          </div>

          <h1 className="text-h1 text-ink-primary">Create your account</h1>
          <p className="mt-2 text-body-sm text-ink-secondary">
            Already have one?{" "}
            <Link to="/login" className="text-brand-primary hover:underline">
              Sign in
            </Link>
            .
          </p>

          <form onSubmit={handleSubmit} noValidate className="mt-8 space-y-5">
            <div className="grid grid-cols-2 gap-4">
              <Input
                label="First name"
                id="firstName"
                autoComplete="given-name"
                autoFocus
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
              />
              <Input
                label="Last name"
                id="lastName"
                autoComplete="family-name"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
              />
            </div>

            <Input
              label="Email address"
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
            />

            <Input
              label="Phone number"
              id="phone"
              type="tel"
              autoComplete="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+1-555-0100"
            />

            <Input
              label="Date of birth"
              id="dateOfBirth"
              type="date"
              autoComplete="bday"
              value={dateOfBirth}
              onChange={(e) => setDateOfBirth(e.target.value)}
            />

            <Input
              label="Address"
              id="address"
              autoComplete="street-address"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
            />

            <div>
              <label htmlFor="password" className="block text-label text-ink-secondary mb-1.5">
                Password
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full h-11 px-3.5 pr-11 text-body text-ink-primary bg-white border border-surface-border rounded-md transition-colors placeholder:text-ink-muted focus-visible:border-brand-primary"
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
              <p className="mt-1.5 text-caption text-ink-muted">
                At least 8 characters, including a letter and a number.
              </p>
            </div>

            <Input
              label="Confirm password"
              id="confirmPassword"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="••••••••"
            />

            {error && (
              <p className="text-caption text-semantic-error" role="alert">
                {error}
              </p>
            )}

            <Button type="submit" variant="primary" size="lg" fullWidth loading={submitting}>
              {submitting ? "Creating account…" : "Create account"}
            </Button>
          </form>

          <div className="mt-6 flex items-center gap-2 text-caption text-ink-muted">
            <Icon name="shield-check" size={16} />
            Your password is hashed before it's ever stored — BankSphere never sees it in plain text.
          </div>
        </div>
      </div>
    </div>
  );
}
