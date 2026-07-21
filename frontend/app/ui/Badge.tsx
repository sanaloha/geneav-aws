import clsx from "clsx";
import type { ReactNode } from "react";

export type BadgeTone = "brand" | "success" | "neutral" | "danger" | "warning";

const tones: Record<BadgeTone, string> = {
  brand: "bg-brand-bg text-brand-fg border-[#bfdbfe]",
  success: "bg-success-bg text-success border-success-border",
  neutral: "bg-surface-sunken text-ink-muted border-line",
  danger: "bg-danger-bg text-danger border-danger-border",
  warning: "bg-warning-bg text-warning border-warning-border",
};

export default function Badge({
  children,
  tone = "brand",
  uppercase = false,
  className,
}: {
  children: ReactNode;
  tone?: BadgeTone;
  /** For status pills (API key state), which read better as small caps. */
  uppercase?: boolean;
  className?: string;
}) {
  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1.5 rounded-full border font-bold",
        uppercase
          ? "px-2 py-0.5 text-[11px] uppercase tracking-wider"
          : "px-3.5 py-1.5 text-[13px]",
        tones[tone],
        className
      )}
    >
      {children}
    </span>
  );
}
