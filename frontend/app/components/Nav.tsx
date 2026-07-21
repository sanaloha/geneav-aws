"use client";

import { Menu, ShieldCheck, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import clsx from "clsx";
import { ButtonLink } from "../ui/Button";

const links = [
  { href: "/features", label: "Features" },
  { href: "/developers", label: "Developers" },
  { href: "/about", label: "About" },
];

/**
 * Client component only because of the mobile menu. It was previously a server
 * component with zero JS; the tradeoff is deliberate — the nav simply crowded
 * and overflowed below ~600px before.
 */
export default function Nav() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  // Route changes must close the menu, or it stays open over the new page.
  useEffect(() => setOpen(false), [pathname]);

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-surface/85 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-content items-center gap-6 px-5">
        <Link
          href="/"
          className="flex items-center gap-2 text-xl font-extrabold tracking-tight text-ink no-underline hover:no-underline"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-brand text-white">
            <ShieldCheck size={17} aria-hidden />
          </span>
          gene<span className="-ml-[3px] text-brand">av</span>
        </Link>

        <nav className="ml-auto hidden items-center gap-7 md:flex">
          {links.map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className={clsx(
                "text-[15px] font-semibold no-underline transition-colors hover:no-underline",
                pathname === href ? "text-ink" : "text-ink-muted hover:text-ink"
              )}
            >
              {label}
            </Link>
          ))}
          <Link
            href="/login"
            className="text-[15px] font-semibold text-ink-muted no-underline transition-colors hover:text-ink hover:no-underline"
          >
            Sign in
          </Link>
          <ButtonLink href="/login" variant="primary" size="sm">
            Get an API key
          </ButtonLink>
        </nav>

        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          aria-controls="mobile-nav"
          aria-label={open ? "Close menu" : "Open menu"}
          className="ml-auto rounded-lg p-2 text-ink-muted hover:bg-surface-sunken hover:text-ink md:hidden"
        >
          {open ? <X size={22} aria-hidden /> : <Menu size={22} aria-hidden />}
        </button>
      </div>

      {open && (
        <nav id="mobile-nav" className="border-t border-line bg-surface px-5 py-4 md:hidden">
          <ul className="m-0 list-none space-y-1 p-0">
            {[...links, { href: "/login", label: "Sign in" }].map(({ href, label }) => (
              <li key={href}>
                <Link
                  href={href}
                  className="block rounded-lg px-3 py-2.5 text-[15px] font-semibold text-ink-muted no-underline hover:bg-surface-sunken hover:text-ink hover:no-underline"
                >
                  {label}
                </Link>
              </li>
            ))}
          </ul>
          <ButtonLink href="/login" variant="primary" fullWidth className="mt-3">
            Get an API key
          </ButtonLink>
        </nav>
      )}
    </header>
  );
}
