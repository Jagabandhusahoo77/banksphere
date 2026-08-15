import { forwardRef, type ButtonHTMLAttributes } from "react";
import Icon, { type IconName } from "./Icon";

type Variant = "primary" | "secondary" | "outline" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  fullWidth?: boolean;
  icon?: IconName;
  iconPosition?: "left" | "right";
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary:
    "bg-brand-primary text-white hover:bg-brand-primary-dark active:bg-brand-primary-dark disabled:bg-brand-primary/50",
  secondary:
    "bg-brand-secondary text-white hover:bg-brand-secondary-dark active:bg-brand-secondary-dark disabled:bg-brand-secondary/50",
  outline:
    "bg-white text-brand-primary border border-surface-border hover:bg-brand-primary-light disabled:text-ink-muted disabled:bg-white",
  ghost: "bg-transparent text-ink-secondary hover:bg-surface-muted disabled:text-ink-muted",
  danger: "bg-semantic-error text-white hover:bg-red-700 disabled:bg-semantic-error/50",
};

const SIZE_CLASSES: Record<Size, string> = {
  sm: "h-9 px-3 text-body-sm gap-1.5",
  md: "h-11 px-5 text-body gap-2",
  lg: "h-12 px-7 text-body font-semibold gap-2.5",
};

const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = "primary",
    size = "md",
    loading = false,
    fullWidth = false,
    icon,
    iconPosition = "left",
    disabled,
    className = "",
    children,
    ...rest
  },
  ref,
) {
  const isDisabled = disabled || loading;

  return (
    <button
      ref={ref}
      disabled={isDisabled}
      aria-busy={loading || undefined}
      className={`inline-flex items-center justify-center rounded-md font-medium transition-colors duration-150 disabled:cursor-not-allowed ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${fullWidth ? "w-full" : ""} ${className}`}
      {...rest}
    >
      {loading ? (
        <Icon name="spinner" size={size === "sm" ? 16 : 18} className="animate-spin" />
      ) : (
        icon && iconPosition === "left" && <Icon name={icon} size={size === "sm" ? 16 : 18} />
      )}
      {children}
      {!loading && icon && iconPosition === "right" && <Icon name={icon} size={size === "sm" ? 16 : 18} />}
    </button>
  );
});

export default Button;
