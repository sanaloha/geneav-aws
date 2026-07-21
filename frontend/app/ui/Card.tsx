import clsx from "clsx";
import type { ReactNode } from "react";

type Props = {
  children: ReactNode;
  /**
   * `.card`, `.result.clean/.infected/.error` and `.reveal` were all the same
   * thing — a bordered, tinted panel — so they collapse into one `tone`.
   */
  tone?: "default" | "success" | "danger" | "warning";
  padding?: "sm" | "md";
  /** Adds a lift on hover. Only for cards that are themselves links. */
  interactive?: boolean;
  className?: string;
};

const tones = {
  default: "bg-surface border-line",
  success: "bg-success-bg border-success-border",
  danger: "bg-danger-bg border-danger-border",
  warning: "bg-warning-bg border-warning-border",
};

export default function Card({
  children,
  tone = "default",
  padding = "md",
  interactive = false,
  className,
}: Props) {
  return (
    <div
      className={clsx(
        "rounded-card border shadow-sm",
        padding === "md" ? "p-6" : "p-4",
        tones[tone],
        interactive && "transition-shadow hover:shadow-md",
        className
      )}
    >
      {children}
    </div>
  );
}
