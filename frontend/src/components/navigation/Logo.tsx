import { Link } from "react-router-dom";
import logo from "@/assets/branding/banksphere-logo.svg";
import logoWhite from "@/assets/branding/banksphere-logo-white.svg";

interface LogoProps {
  variant?: "default" | "white";
  to?: string;
  className?: string;
}

export default function Logo({ variant = "default", to = "/", className = "h-8" }: LogoProps) {
  return (
    <Link to={to} className="inline-flex items-center focus-visible:outline-none">
      <img
        src={variant === "white" ? logoWhite : logo}
        alt="BankSphere"
        className={className}
      />
    </Link>
  );
}
