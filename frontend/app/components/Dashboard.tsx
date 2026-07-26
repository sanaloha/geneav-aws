"use client";

import clsx from "clsx";
import { AlertTriangle, ArrowLeft, Check, Copy, KeyRound, Mail } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState, type ReactNode, type FormEvent } from "react";
import Badge, { type BadgeTone } from "../ui/Badge";
import { Button } from "../ui/Button";
import Card from "../ui/Card";
import { Field, Input } from "../ui/Field";
import { track } from "../lib/analytics";
import { readAttribution } from "../lib/attribution";
import { notifyAuthChanged } from "../lib/authEvents";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

type Me = { email: string; plan: string; authProvider: string };

type Usage = {
  plan: string;
  period: string;
  scansUsed: number;
  scansQuota: number;
  scansRemaining: number;
};

type KeySummary = {
  id: string;
  name: string | null;
  keyPrefix: string;
  lastFour: string;
  status: string;
  createdAt: string;
  lastUsedAt: string | null;
};

// Session cookie auth — every call sends the HttpOnly session cookie.
const withCreds: RequestInit = { credentials: "include" };

/**
 * `status` comes straight from the API, so it is mapped explicitly rather than
 * interpolated into a class name. An unrecognised value previously rendered an
 * unstyled pill with no warning.
 */
const KEY_STATUS_TONE: Record<string, BadgeTone> = {
  active: "success",
  revoked: "neutral",
};

async function apiErrorMessage(res: Response, fallback: string): Promise<string> {
  try {
    const body = await res.json();
    if (body?.message) return body.message as string;
  } catch {
    /* non-JSON body */
  }
  return `${fallback} (HTTP ${res.status}).`;
}

function fmtDate(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

function ErrorNote({ children }: { children: ReactNode }) {
  return (
    <div
      role="alert"
      className="flex items-start gap-2.5 rounded-[10px] border border-danger-border bg-danger-bg p-3 text-sm text-danger"
    >
      <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
      {children}
    </div>
  );
}

export default function Dashboard() {
  const [authState, setAuthState] = useState<"loading" | "anon" | "authed">("loading");
  const [me, setMe] = useState<Me | null>(null);

  // Landing (sign in / sign up / forgot password) form
  const [mode, setMode] = useState<"signin" | "signup" | "forgot">("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [resetSent, setResetSent] = useState(false);

  // Dashboard data
  const [usage, setUsage] = useState<Usage | null>(null);
  const [keys, setKeys] = useState<KeySummary[] | null>(null);
  const [dataError, setDataError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // One-time plaintext key reveal after creating a key
  const [revealed, setRevealed] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [newKeyName, setNewKeyName] = useState("");
  const [confirmRevoke, setConfirmRevoke] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setDataError(null);
    try {
      const [uRes, kRes] = await Promise.all([
        fetch(`${API_BASE}/api/v1/usage`, withCreds),
        fetch(`${API_BASE}/api/v1/keys`, withCreds),
      ]);
      if (uRes.status === 401 || kRes.status === 401) {
        setAuthState("anon");
        setMe(null);
        return;
      }
      if (uRes.ok) setUsage(await uRes.json());
      if (kRes.ok) setKeys(await kRes.json());
    } catch {
      setDataError(`Could not reach the API at ${API_BASE}.`);
    } finally {
      setLoading(false);
    }
  }, []);

  // On mount, ask the server whether we already have a session.
  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/me`, withCreds);
        if (res.ok) {
          setMe(await res.json());
          setAuthState("authed");
        } else {
          setAuthState("anon");
        }
      } catch {
        setAuthState("anon");
      }
    })();
  }, []);

  useEffect(() => {
    if (authState === "authed") loadData();
  }, [authState, loadData]);

  const onAuth = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setAuthBusy(true);
      setAuthError(null);
      const signingUp = mode === "signup";
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/${signingUp ? "signup" : "login"}`, {
          ...withCreds,
          method: "POST",
          headers: { "Content-Type": "application/json" },
          // Attribution only belongs on a signup — this handler is shared with
          // login, and where a returning user came from is not something we
          // record.
          body: JSON.stringify(
            signingUp ? { email, password, attribution: readAttribution() } : { email, password }
          ),
        });
        if (!res.ok) {
          setAuthError(await apiErrorMessage(res, signingUp ? "Sign up failed" : "Sign in failed"));
          return;
        }
        setMe(await res.json());
        setPassword("");
        setAuthState("authed");
        if (signingUp) track("signup");
        notifyAuthChanged();
      } catch {
        setAuthError(`Could not reach the API at ${API_BASE}.`);
      } finally {
        setAuthBusy(false);
      }
    },
    [mode, email, password]
  );

  const onForgot = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setAuthBusy(true);
      setAuthError(null);
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/forgot-password`, {
          ...withCreds,
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email }),
        });
        if (!res.ok) {
          setAuthError(await apiErrorMessage(res, "Could not send the reset email"));
          return;
        }
        // Deliberately the same message whether or not an account exists — the
        // API does not reveal it, and neither should the UI.
        setResetSent(true);
      } catch {
        setAuthError(`Could not reach the API at ${API_BASE}.`);
      } finally {
        setAuthBusy(false);
      }
    },
    [email]
  );

  const onCreateKey = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      try {
        const res = await fetch(`${API_BASE}/api/v1/keys`, {
          ...withCreds,
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name: newKeyName.trim() || "key" }),
        });
        if (!res.ok) {
          setDataError(await apiErrorMessage(res, "Could not create key"));
          return;
        }
        const body = await res.json();
        setRevealed(body.apiKey);
        setNewKeyName("");
        // The activation event: a signup that never gets here learned nothing
        // about the product. See docs/marketing-plan.md §6.
        track("api-key-created");
        loadData();
      } catch {
        setDataError(`Could not reach the API at ${API_BASE}.`);
      }
    },
    [newKeyName, loadData]
  );

  const onRevoke = useCallback(
    async (id: string) => {
      setConfirmRevoke(null);
      try {
        const res = await fetch(`${API_BASE}/api/v1/keys/${id}`, { ...withCreds, method: "DELETE" });
        if (!res.ok && res.status !== 404) {
          setDataError(await apiErrorMessage(res, "Could not revoke key"));
          return;
        }
        loadData();
      } catch {
        setDataError(`Could not reach the API at ${API_BASE}.`);
      }
    },
    [loadData]
  );

  const copyRevealed = useCallback(() => {
    if (!revealed) return;
    navigator.clipboard?.writeText(revealed).then(
      () => {
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      },
      () => {}
    );
  }, [revealed]);

  if (authState === "loading") {
    return <p className="text-center text-ink-muted">Loading…</p>;
  }

  // ---- Signed out: forgot password ----------------------------------------
  if (authState === "anon" && mode === "forgot") {
    return (
      <div className="mx-auto max-w-md">
        <h1 className="sr-only">Reset your password</h1>
        <Card>
          <h2 className="m-0 text-xl font-bold text-ink">Reset your password</h2>

          {resetSent ? (
            <>
              <div className="mt-4 flex items-start gap-2.5 rounded-[10px] border border-success-border bg-success-bg p-3 text-sm text-success">
                <Mail size={16} className="mt-0.5 shrink-0" aria-hidden />
                <span>
                  If an account exists for <strong>{email}</strong>, we&apos;ve sent a reset link
                  to it. The link expires in 10 minutes.
                </span>
              </div>
              <p className="mt-3 text-[13px] text-ink-muted">
                Nothing arrived? Check your spam folder, then try again.
              </p>
            </>
          ) : (
            <>
              <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
                Enter your email and we&apos;ll send you a link to set a new password.
              </p>

              {authError && (
                <div className="mt-4">
                  <ErrorNote>{authError}</ErrorNote>
                </div>
              )}

              <form onSubmit={onForgot} className="mt-5">
                <Field label="Email">
                  <Input
                    type="email"
                    required
                    autoComplete="email"
                    placeholder="you@company.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </Field>
                <Button type="submit" variant="primary" fullWidth disabled={authBusy}>
                  {authBusy ? "Sending…" : "Send reset link"}
                </Button>
              </form>
            </>
          )}

          <button
            type="button"
            onClick={() => {
              setMode("signin");
              setAuthError(null);
              setResetSent(false);
            }}
            className="mt-4 inline-flex items-center gap-1.5 text-[13px] font-semibold text-ink-muted hover:text-brand"
          >
            <ArrowLeft size={14} aria-hidden /> Back to sign in
          </button>
        </Card>
      </div>
    );
  }

  // ---- Signed out: sign in / sign up --------------------------------------
  if (authState === "anon") {
    return (
      <>
        {/* No visible "Login" title — the tabbed card is self-explanatory — but
            the page still needs one <h1> for assistive tech and SEO. */}
        <h1 className="sr-only">Sign in to geneav</h1>
        <p className="mb-7 text-center text-[17px] leading-relaxed text-ink-muted">
          Manage your API keys and track your usage.
        </p>
        <div className="mx-auto max-w-md">
        <Card>
          <div className="mb-5 flex gap-1.5 rounded-xl border border-line bg-surface-sunken p-1">
            {(["signin", "signup"] as const).map((m) => (
              <button
                key={m}
                type="button"
                className={clsx(
                  "flex-1 rounded-[9px] py-2 text-sm font-bold transition-colors",
                  mode === m ? "bg-surface text-ink shadow-sm" : "text-ink-muted hover:text-ink"
                )}
                onClick={() => {
                  setMode(m);
                  setAuthError(null);
                }}
              >
                {m === "signin" ? "Sign in" : "Sign up"}
              </button>
            ))}
          </div>

          {authError && (
            <div className="mb-4">
              <ErrorNote>{authError}</ErrorNote>
            </div>
          )}

          <form onSubmit={onAuth}>
            <Field label="Email">
              <Input
                type="email"
                required
                autoComplete="email"
                placeholder="you@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </Field>
            <Field
              label="Password"
              hint={
                mode === "signup"
                  ? "At least 12 characters, mixing three of: lowercase, uppercase, digits, symbols."
                  : undefined
              }
            >
              <Input
                type="password"
                required
                minLength={mode === "signup" ? 12 : undefined}
                autoComplete={mode === "signup" ? "new-password" : "current-password"}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>
            <Button type="submit" variant="primary" fullWidth disabled={authBusy}>
              {authBusy ? "Please wait…" : mode === "signup" ? "Create account" : "Sign in"}
            </Button>

            {mode === "signup" && (
              <p className="mt-3 text-center text-[13px] leading-relaxed text-ink-muted">
                By creating an account, you agree to our{" "}
                <Link href="/terms" className="font-semibold text-brand hover:text-brand-hover">
                  Terms of Service
                </Link>{" "}
                and{" "}
                <Link href="/privacy" className="font-semibold text-brand hover:text-brand-hover">
                  Privacy Policy
                </Link>
                .
              </p>
            )}
          </form>

          {mode === "signin" && (
            <button
              type="button"
              onClick={() => {
                setMode("forgot");
                setAuthError(null);
                setPassword("");
              }}
              className="mt-3 text-[13px] font-semibold text-ink-muted hover:text-brand"
            >
              Forgot password?
            </button>
          )}

          <div className="my-4 flex items-center gap-3 text-xs text-ink-subtle">
            <span className="h-px flex-1 bg-line" />
            or
            <span className="h-px flex-1 bg-line" />
          </div>

          <Button variant="secondary" fullWidth disabled title="Available soon">
            Continue with Google
            <span className="text-[13px] font-medium text-ink-subtle">(soon)</span>
          </Button>
        </Card>
        </div>
      </>
    );
  }

  // ---- Signed in ----------------------------------------------------------
  const pct =
    usage && usage.scansQuota > 0
      ? Math.min(100, Math.round((usage.scansUsed / usage.scansQuota) * 100))
      : 0;

  return (
    <div>
      {/* Visually removed once signed in — "Login" is wrong at that point — but
          the page still needs a heading for assistive tech. */}
      <h1 className="sr-only">Dashboard</h1>

      {revealed && (
        <Card tone="success" className="mb-5">
          <h3 className="m-0 flex items-center gap-2 text-base font-bold text-success">
            <KeyRound size={18} aria-hidden />
            Save your API key now
          </h3>
          <p className="mb-3 mt-1.5 text-sm text-ink-muted">
            This is the only time it will be shown. Store it somewhere safe.
          </p>
          <div className="flex flex-wrap items-center gap-2.5">
            <code className="min-w-[220px] flex-1 break-all rounded-[10px] border border-line bg-surface px-3 py-2.5 text-[13px] text-ink">
              {revealed}
            </code>
            <Button variant="primary" size="sm" onClick={copyRevealed}>
              {copied ? <Check size={15} aria-hidden /> : <Copy size={15} aria-hidden />}
              {copied ? "Copied" : "Copy"}
            </Button>
          </div>
          <button
            type="button"
            onClick={() => setRevealed(null)}
            className="mt-3 text-[13px] font-semibold text-ink-muted hover:text-brand"
          >
            I&apos;ve saved it — dismiss
          </button>
        </Card>
      )}

      <div className="mb-5 flex flex-wrap items-center gap-2.5">
        {usage && <Badge tone="brand">{usage.plan} plan</Badge>}
        {me && <span className="text-[13px] text-ink-muted">{me.email}</span>}
      </div>

      {dataError && (
        <div className="mb-5">
          <ErrorNote>{dataError}</ErrorNote>
        </div>
      )}

      <Card className="mb-5">
        <h3 className="m-0 text-base font-bold text-ink">
          Usage this month{" "}
          {usage && <span className="text-[13px] font-medium text-ink-muted">· {usage.period}</span>}
        </h3>
        {loading && !usage ? (
          <p className="m-0 mt-2 text-ink-muted">Loading…</p>
        ) : usage ? (
          <>
            <p className="mb-3 mt-1 flex items-baseline gap-2">
              <strong className="text-2xl font-extrabold text-ink">
                {usage.scansUsed.toLocaleString()}
              </strong>
              <span className="text-[15px] text-ink-muted">
                / {usage.scansQuota.toLocaleString()} scans ·{" "}
                {usage.scansRemaining.toLocaleString()} left
              </span>
            </p>
            <div className="h-2.5 overflow-hidden rounded-full border border-line bg-surface-sunken">
              {/* Width is computed, so it stays an inline style: Tailwind's JIT
                  scans statically and would emit no class for w-[${pct}%]. */}
              <div
                className={clsx(
                  "h-full transition-[width] duration-300",
                  pct >= 100 ? "bg-danger" : "bg-brand"
                )}
                style={{ width: `${pct}%` }}
              />
            </div>
          </>
        ) : null}
      </Card>

      <Card>
        <h3 className="m-0 text-base font-bold text-ink">API keys</h3>
        <p className="mb-3 mt-1 text-[13px] text-ink-muted">
          Use a key as <code className="text-ink">Authorization: Bearer …</code> to call the API.
        </p>
        <form onSubmit={onCreateKey} className="mb-5 mt-2 flex flex-wrap gap-2.5">
          <Input
            className="min-w-[200px] flex-1"
            placeholder="Key name (e.g. production)"
            value={newKeyName}
            onChange={(e) => setNewKeyName(e.target.value)}
          />
          <Button type="submit" variant="primary" size="sm">
            Create key
          </Button>
        </form>

        {keys && keys.length > 0 ? (
          <div className="flex flex-col gap-2.5">
            {keys.map((k) => (
              <div
                key={k.id}
                className="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-surface-subtle px-3.5 py-3"
              >
                <div className="flex flex-wrap items-center gap-2.5">
                  <code className="text-[13px] text-ink">
                    {k.keyPrefix}…{k.lastFour}
                  </code>
                  <Badge tone={KEY_STATUS_TONE[k.status] ?? "neutral"} uppercase>
                    {k.status}
                  </Badge>
                  <span className="text-[13px] text-ink-muted">{k.name || "—"}</span>
                </div>
                <div className="flex flex-wrap gap-4 text-[12.5px] text-ink-muted sm:ml-auto">
                  <span>created {fmtDate(k.createdAt)}</span>
                  <span>last used {fmtDate(k.lastUsedAt)}</span>
                </div>
                <div className="flex items-center gap-1.5">
                  {k.status !== "active" ? (
                    <span className="text-[13px] text-ink-subtle">revoked</span>
                  ) : confirmRevoke === k.id ? (
                    <>
                      <Button variant="danger" size="sm" onClick={() => onRevoke(k.id)}>
                        Confirm
                      </Button>
                      <Button variant="link" onClick={() => setConfirmRevoke(null)}>
                        Cancel
                      </Button>
                    </>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmRevoke(k.id)}
                      className="text-[13px] font-semibold text-danger hover:text-danger-hover"
                    >
                      Revoke
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="m-0 mt-2 text-ink-muted">
            {loading ? "Loading…" : "No keys yet — create one above."}
          </p>
        )}
      </Card>
    </div>
  );
}
