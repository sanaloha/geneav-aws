import clsx from "clsx";
import Link from "next/link";
import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ReactNode } from "react";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "link";
export type ButtonSize = "sm" | "md" | "lg";

const variants: Record<ButtonVariant, string> = {
  primary: "bg-brand text-white border-transparent shadow-sm hover:bg-brand-hover hover:shadow-md",
  secondary: "bg-surface text-ink border-line hover:border-brand hover:text-brand",
  ghost: "bg-transparent text-ink-muted border-transparent hover:bg-surface-sunken hover:text-ink",
  danger: "bg-danger text-white border-transparent hover:bg-danger-hover",
  // Absorbs the old `.linklike` button-styled-as-text.
  link: "bg-transparent text-ink-muted border-transparent p-0 font-semibold hover:text-brand hover:underline",
};

const sizes: Record<ButtonSize, string> = {
  sm: "px-3.5 py-2 text-sm",
  md: "px-5 py-3 text-[15px]",
  lg: "px-6 py-3.5 text-base",
};

export function buttonClasses(
  variant: ButtonVariant = "primary",
  size: ButtonSize = "md",
  fullWidth = false
) {
  return clsx(
    "inline-flex items-center justify-center gap-2 rounded-[10px] border font-bold",
    "transition-colors transition-shadow",
    "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand",
    "disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:shadow-none",
    variant !== "link" && sizes[size],
    variants[variant],
    fullWidth && "w-full"
  );
}

type BaseProps = {
  variant?: ButtonVariant;
  size?: ButtonSize;
  fullWidth?: boolean;
  children: ReactNode;
};

/**
 * Two components rather than one polymorphic `as` prop: both cases genuinely
 * exist here (Link in the marketing pages, button in the dashboard) and the
 * generics needed to unify them are not worth it at this size.
 */
export function Button({
  variant,
  size,
  fullWidth,
  className,
  children,
  ...rest
}: BaseProps & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button className={clsx(buttonClasses(variant, size, fullWidth), className)} {...rest}>
      {children}
    </button>
  );
}

export function ButtonLink({
  variant,
  size,
  fullWidth,
  className,
  href,
  children,
  ...rest
}: BaseProps & { href: string } & AnchorHTMLAttributes<HTMLAnchorElement>) {
  const classes = clsx(buttonClasses(variant, size, fullWidth), "no-underline hover:no-underline", className);
  // Next's Link cannot handle external or mailto targets.
  if (/^(https?:|mailto:|#)/.test(href)) {
    return (
      <a href={href} className={classes} {...rest}>
        {children}
      </a>
    );
  }
  return (
    <Link href={href} className={classes} {...rest}>
      {children}
    </Link>
  );
}
