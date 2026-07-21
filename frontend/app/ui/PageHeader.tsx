import clsx from "clsx";
import type { ReactNode } from "react";

/**
 * Every non-landing page previously opened with a bare <h2>, so the site had
 * exactly one <h1> across all routes. `level` defaults to h1 for that reason;
 * pass "h2" for section headers within a page.
 */
export default function PageHeader({
  eyebrow,
  title,
  lead,
  align = "left",
  level = "h1",
  className,
}: {
  eyebrow?: ReactNode;
  title: string;
  lead?: ReactNode;
  align?: "left" | "center";
  level?: "h1" | "h2";
  className?: string;
}) {
  const Heading = level;
  return (
    <div className={clsx(align === "center" && "text-center", className)}>
      {eyebrow && <div className={clsx("mb-4", align === "center" && "flex justify-center")}>{eyebrow}</div>}
      <Heading
        className={clsx(
          "m-0 font-extrabold tracking-tight text-ink",
          level === "h1" ? "text-3xl sm:text-4xl" : "text-2xl sm:text-3xl"
        )}
      >
        {title}
      </Heading>
      {lead && (
        <p
          className={clsx(
            "mb-7 mt-3 text-[17px] leading-relaxed text-ink-muted",
            align === "center" && "mx-auto max-w-2xl"
          )}
        >
          {lead}
        </p>
      )}
    </div>
  );
}
