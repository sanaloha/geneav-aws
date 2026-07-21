import clsx from "clsx";
import type { InputHTMLAttributes, ReactNode } from "react";

/**
 * The focus ring lives here, once. The legacy `.input:focus` removed the
 * outline outright and replaced it with a border-colour change no keyboard
 * user could reliably see; every input in the app shared that bug.
 */
export function Input({
  invalid,
  className,
  ...rest
}: { invalid?: boolean } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      aria-invalid={invalid || undefined}
      className={clsx(
        "w-full rounded-[10px] border bg-surface px-3 py-2.5 text-sm text-ink",
        "placeholder:text-ink-subtle",
        "transition-colors",
        "focus-visible:outline-none focus-visible:border-brand focus-visible:ring-4 focus-visible:ring-brand/15",
        invalid ? "border-danger" : "border-line-control",
        className
      )}
      {...rest}
    />
  );
}

export function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: ReactNode;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="mb-3.5 block">
      <span className="mb-1.5 block text-[13px] font-semibold text-ink-muted">{label}</span>
      {children}
      {hint && !error && <span className="mt-1.5 block text-[13px] text-ink-muted">{hint}</span>}
      {error && (
        <span role="alert" className="mt-1.5 block text-[13px] font-medium text-danger">
          {error}
        </span>
      )}
    </label>
  );
}
