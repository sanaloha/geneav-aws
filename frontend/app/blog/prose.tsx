import clsx from "clsx";
import type { ReactNode } from "react";

/**
 * Typography primitives for post bodies.
 *
 * Preflight is on and there is no @tailwindcss/typography plugin, so prose
 * elements need explicit classes. These wrap that up in about a dozen named
 * pieces rather than adding a plugin whose defaults would then have to be
 * fought back into the theme tokens.
 *
 * Measure is capped at 68ch on the body wrapper — `max-w-content` is 1040px,
 * which is right for a marketing grid and far too wide for running text.
 */

export function Prose({ children }: { children: ReactNode }) {
  return <div className="max-w-[68ch]">{children}</div>;
}

export function P({ children }: { children: ReactNode }) {
  return <p className="mt-5 text-[17px] leading-relaxed text-ink-muted">{children}</p>;
}

/** Opening paragraph: slightly larger, sets up the argument. */
export function Lead({ children }: { children: ReactNode }) {
  return <p className="mt-6 text-lg leading-relaxed text-ink-muted">{children}</p>;
}

export function H2({ children }: { children: ReactNode }) {
  return (
    <h2 className="mb-0 mt-12 text-2xl font-extrabold tracking-tight text-ink sm:text-[26px]">
      {children}
    </h2>
  );
}

export function H3({ children }: { children: ReactNode }) {
  return <h3 className="mb-0 mt-9 text-lg font-bold text-ink">{children}</h3>;
}

export function UL({ children }: { children: ReactNode }) {
  return <ul className="mt-5 list-disc space-y-2.5 pl-5 text-[17px] leading-relaxed text-ink-muted">{children}</ul>;
}

export function OL({ children }: { children: ReactNode }) {
  return <ol className="mt-5 list-decimal space-y-2.5 pl-5 text-[17px] leading-relaxed text-ink-muted">{children}</ol>;
}

export function LI({ children }: { children: ReactNode }) {
  return <li className="pl-1">{children}</li>;
}

export function Strong({ children }: { children: ReactNode }) {
  return <strong className="font-bold text-ink">{children}</strong>;
}

export function A({ href, children }: { href: string; children: ReactNode }) {
  const external = href.startsWith("http");
  return (
    <a
      href={href}
      className="font-semibold text-brand underline decoration-brand/30 underline-offset-2 hover:decoration-brand"
      {...(external ? { target: "_blank", rel: "noreferrer noopener" } : {})}
    >
      {children}
    </a>
  );
}

export function Code({ children }: { children: ReactNode }) {
  return (
    <code className="rounded bg-surface-sunken px-1.5 py-0.5 font-mono text-[0.875em] text-ink">
      {children}
    </code>
  );
}

/**
 * Code blocks scroll inside their own box. Long request/response samples must
 * never make the page itself scroll sideways on a phone.
 */
export function Pre({ children, label }: { children: ReactNode; label?: string }) {
  return (
    <figure className="mt-6">
      {label && (
        <figcaption className="mb-2 font-mono text-xs uppercase tracking-wider text-ink-subtle">
          {label}
        </figcaption>
      )}
      <pre className="overflow-x-auto rounded-card border border-line bg-surface-sunken p-4 font-mono text-[13px] leading-relaxed text-ink">
        {children}
      </pre>
    </figure>
  );
}

export function Quote({ children, cite }: { children: ReactNode; cite?: string }) {
  return (
    <blockquote className="mt-6 border-l-4 border-brand/40 pl-5">
      <div className="text-[17px] italic leading-relaxed text-ink-muted">{children}</div>
      {cite && <footer className="mt-2 text-sm not-italic text-ink-subtle">— {cite}</footer>}
    </blockquote>
  );
}

/**
 * A tinted panel for a caveat or concession. `warning` is the honest-limitation
 * case, which these posts use deliberately and often.
 */
export function Note({
  children,
  tone = "default",
  title,
}: {
  children: ReactNode;
  tone?: "default" | "warning";
  title?: string;
}) {
  return (
    <aside
      className={clsx(
        "mt-6 rounded-card border p-5",
        tone === "warning" ? "border-warning-border bg-warning-bg" : "border-line bg-surface-subtle"
      )}
    >
      {title && (
        <div
          className={clsx(
            "mb-1.5 text-sm font-bold",
            tone === "warning" ? "text-warning" : "text-ink"
          )}
        >
          {title}
        </div>
      )}
      <div className="text-[15px] leading-relaxed text-ink-muted">{children}</div>
    </aside>
  );
}

/** Wide tables scroll in their own container rather than widening the page. */
export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="mt-6 overflow-x-auto rounded-card border border-line">
      <table className="w-full border-collapse text-left text-[15px]">{children}</table>
    </div>
  );
}

export function TH({ children }: { children: ReactNode }) {
  return (
    <th className="border-b border-line bg-surface-subtle px-4 py-2.5 font-bold text-ink">
      {children}
    </th>
  );
}

export function TD({ children }: { children: ReactNode }) {
  return <td className="border-b border-line px-4 py-2.5 align-top text-ink-muted">{children}</td>;
}
