"use client";

import { Menu, ShieldCheck, X } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import clsx from "clsx";
import { AUTH_CHANGED, notifyAuthChanged } from "../lib/authEvents";
import { Button, ButtonLink } from "../ui/Button";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

const links = [
  { href: "/features", label: "Features" },
  { href: "/developers", label: "Developers" },
  { href: "/blog", label: "Blog" },
  { href: "/about", label: "About" },
];

const linkClass = (active: boolean) =>
  clsx(
    "text-[15px] font-semibold no-underline transition-colors hover:no-underline",
    active ? "text-ink" : "text-ink-muted hover:text-ink"
  );

/**
 * Prefix match, so a blog post at /blog/some-slug still highlights "Blog".
 * Exact match alone left the nav with nothing active on every post page.
 */
const isActive = (pathname: string, href: string) =>
  pathname === href || pathname.startsWith(`${href}/`);

/**
 * Client component for the mobile menu and the session-dependent links. It was
 * previously a server component with zero JS; the tradeoff is deliberate — the
 * nav crowded and overflowed below ~600px, and it showed "Sign in" to people
 * who were already signed in.
 */
export default function Nav() {
  const [open, setOpen] = useState(false);
  // null = not yet known. The auth links render nothing until it resolves, so
  // the nav never flashes "Sign in" at someone who is already signed in.
  const [authed, setAuthed] = useState<boolean | null>(null);
  const pathname = usePathname();
  const router = useRouter();

  // Route changes must close the menu, or it stays open over the new page.
  useEffect(() => setOpen(false), [pathname]);

  // Re-checked per route, and on the auth-changed event so signing in from the
  // dashboard updates the nav without waiting for a navigation.
  useEffect(() => {
    let cancelled = false;
    const check = async () => {
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/me`, { credentials: "include" });
        if (!cancelled) setAuthed(res.ok);
      } catch {
        if (!cancelled) setAuthed(false);
      }
    };
    check();
    window.addEventListener(AUTH_CHANGED, check);
    return () => {
      cancelled = true;
      window.removeEventListener(AUTH_CHANGED, check);
    };
  }, [pathname]);

  const signOut = useCallback(async () => {
    try {
      await fetch(`${API_BASE}/api/v1/auth/logout`, {
        method: "POST",
        credentials: "include",
      });
    } catch {
      /* Signing out locally regardless: a failed call must not strand the user. */
    }
    setAuthed(false);
    setOpen(false);
    notifyAuthChanged();
    // Send them home and refresh so any signed-in view unmounts.
    router.push("/");
    router.refresh();
  }, [router]);

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
            <Link key={href} href={href} className={linkClass(isActive(pathname, href))}>
              {label}
            </Link>
          ))}

          {authed === true && (
            <>
              <Link href="/login" className={linkClass(pathname === "/login")}>
                Dashboard
              </Link>
              <Button variant="secondary" size="sm" onClick={signOut}>
                Sign out
              </Button>
            </>
          )}

          {authed === false && (
            <>
              <Link href="/login" className={linkClass(pathname === "/login")}>
                Sign in
              </Link>
              <ButtonLink href="/login" variant="primary" size="sm">
                Get an API key
              </ButtonLink>
            </>
          )}
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
            {links.map(({ href, label }) => (
              <li key={href}>
                <Link
                  href={href}
                  className="block rounded-lg px-3 py-2.5 text-[15px] font-semibold text-ink-muted no-underline hover:bg-surface-sunken hover:text-ink hover:no-underline"
                >
                  {label}
                </Link>
              </li>
            ))}
            {authed !== null && (
              <li>
                <Link
                  href="/login"
                  className="block rounded-lg px-3 py-2.5 text-[15px] font-semibold text-ink-muted no-underline hover:bg-surface-sunken hover:text-ink hover:no-underline"
                >
                  {authed ? "Dashboard" : "Sign in"}
                </Link>
              </li>
            )}
          </ul>

          {authed === true && (
            <Button variant="secondary" fullWidth className="mt-3" onClick={signOut}>
              Sign out
            </Button>
          )}
          {authed === false && (
            <ButtonLink href="/login" variant="primary" fullWidth className="mt-3">
              Get an API key
            </ButtonLink>
          )}
        </nav>
      )}
    </header>
  );
}
