"use client";

import { AlertTriangle, BadgeCheck, ExternalLink, ShoppingCart } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import Badge from "../ui/Badge";
import { Button, ButtonLink } from "../ui/Button";
import Card from "../ui/Card";
import { track } from "../lib/analytics";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const MICROSOFT_LOGIN_ENABLED = process.env.NEXT_PUBLIC_MICROSOFT_LOGIN_ENABLED === "true";

/** Survives the Microsoft sign-in redirect; the URL token does not. */
const TOKEN_STORAGE_KEY = "geneav.marketplace.token";

const withCreds: RequestInit = { credentials: "include" };

type Subscription = {
  subscriptionId: string;
  offerId: string;
  marketplacePlanId: string;
  planKey: string;
  status: string;
  freeTrial: boolean;
  autoRenew: boolean | null;
  termStart: string | null;
  termEnd: string | null;
  beneficiaryEmail: string | null;
};

type Phase =
  | { kind: "loading" }
  | { kind: "signin" }
  | { kind: "confirm"; sub: Subscription }
  | { kind: "activated"; sub: Subscription }
  | { kind: "manage"; sub: Subscription | null }
  | { kind: "error"; message: string };

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
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleDateString();
}

/** Starts Microsoft sign-in, asking the backend to return to this page after. */
function signInWithMicrosoft() {
  // Short-lived, path-only cookie the OIDC success handler reads and clears.
  document.cookie = "geneav_next=/marketplace/landing; path=/; max-age=600; samesite=lax";
  window.location.href = `${API_BASE}/oauth2/authorization/microsoft`;
}

/**
 * The "lobby" for an Azure Marketplace purchase. Microsoft sends the buyer
 * here with a `token` query parameter after purchase (and again, without a
 * fresh purchase, when they choose "Configure account" on an existing
 * subscription): resolve the token, confirm what was bought, activate.
 */
export default function MarketplaceLanding() {
  const [phase, setPhase] = useState<Phase>({ kind: "loading" });
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    // Capture the token before anything can redirect us away.
    const url = new URL(window.location.href);
    const token = url.searchParams.get("token");
    if (token) {
      sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
      // Strip it from the address bar: it is a bearer credential for 24 hours.
      url.searchParams.delete("token");
      window.history.replaceState(null, "", url.toString());
    }

    (async () => {
      let signedIn = false;
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/me`, withCreds);
        signedIn = res.ok;
      } catch {
        setPhase({ kind: "error", message: `Could not reach the API at ${API_BASE}.` });
        return;
      }
      if (!signedIn) {
        setPhase({ kind: "signin" });
        return;
      }

      const stored = sessionStorage.getItem(TOKEN_STORAGE_KEY);
      if (stored) {
        try {
          const res = await fetch(`${API_BASE}/api/v1/marketplace/resolve`, {
            ...withCreds,
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ token: stored }),
          });
          if (!res.ok) {
            setPhase({
              kind: "error",
              message: await apiErrorMessage(res, "Could not identify this purchase"),
            });
            return;
          }
          const sub: Subscription = await res.json();
          sessionStorage.removeItem(TOKEN_STORAGE_KEY);
          if (sub.status === "Subscribed") {
            setPhase({ kind: "manage", sub });
          } else {
            setPhase({ kind: "confirm", sub });
          }
          return;
        } catch {
          setPhase({ kind: "error", message: `Could not reach the API at ${API_BASE}.` });
          return;
        }
      }

      // No token: the buyer is here to manage an existing subscription.
      try {
        const res = await fetch(`${API_BASE}/api/v1/marketplace/subscription`, withCreds);
        setPhase({ kind: "manage", sub: res.ok ? await res.json() : null });
      } catch {
        setPhase({ kind: "manage", sub: null });
      }
    })();
  }, []);

  const onActivate = useCallback(async () => {
    if (phase.kind !== "confirm") return;
    setBusy(true);
    try {
      const res = await fetch(`${API_BASE}/api/v1/marketplace/activate`, {
        ...withCreds,
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ subscriptionId: phase.sub.subscriptionId }),
      });
      if (!res.ok) {
        setPhase({ kind: "error", message: await apiErrorMessage(res, "Activation failed") });
        return;
      }
      const sub: Subscription = await res.json();
      track("marketplace-activated");
      setPhase({ kind: "activated", sub });
    } catch {
      setPhase({ kind: "error", message: `Could not reach the API at ${API_BASE}.` });
    } finally {
      setBusy(false);
    }
  }, [phase]);

  if (phase.kind === "loading") {
    return <p className="text-center text-ink-muted">Checking your purchase…</p>;
  }

  if (phase.kind === "signin") {
    return (
      <div className="mx-auto max-w-md">
        <Card>
          <h1 className="m-0 flex items-center gap-2 text-xl font-bold text-ink">
            <ShoppingCart size={20} className="text-brand" aria-hidden />
            Finish setting up your subscription
          </h1>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Sign in so we can link your Azure Marketplace purchase to a geneav account. Use the
            Microsoft account you purchased with for the smoothest setup.
          </p>
          {MICROSOFT_LOGIN_ENABLED ? (
            <Button variant="primary" fullWidth className="mt-4" onClick={signInWithMicrosoft}>
              Sign in with Microsoft
            </Button>
          ) : (
            <p className="mt-4 rounded-[10px] border border-line bg-surface-sunken p-3 text-sm text-ink-muted">
              Microsoft sign-in is not enabled on this deployment yet.
            </p>
          )}
          <p className="mt-3 text-center text-[13px] text-ink-muted">
            Prefer email &amp; password?{" "}
            <Link href="/login" className="font-semibold text-brand hover:text-brand-hover">
              Sign in here
            </Link>{" "}
            and then return to this page — your purchase is kept for you.
          </p>
        </Card>
      </div>
    );
  }

  if (phase.kind === "error") {
    return (
      <div className="mx-auto max-w-md">
        <Card>
          <h1 className="m-0 flex items-center gap-2 text-xl font-bold text-danger">
            <AlertTriangle size={20} aria-hidden />
            We couldn&apos;t complete this step
          </h1>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">{phase.message}</p>
          <p className="mt-3 text-[13px] text-ink-muted">
            Need a hand? Email{" "}
            <a href="mailto:admin@geneav.com" className="font-semibold text-brand">
              admin@geneav.com
            </a>
            .
          </p>
        </Card>
      </div>
    );
  }

  if (phase.kind === "manage" && !phase.sub) {
    return (
      <div className="mx-auto max-w-md">
        <Card>
          <h1 className="m-0 text-xl font-bold text-ink">No marketplace subscription</h1>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            This account has no Azure Marketplace subscription. If you just purchased one, reopen
            it in the Azure portal and select <strong>Configure account</strong> to arrive here
            with your purchase attached.
          </p>
          <ButtonLink href="/login" variant="secondary" className="mt-4">
            Go to your dashboard
          </ButtonLink>
        </Card>
      </div>
    );
  }

  // confirm / activated / manage-with-subscription all render the details card.
  const sub = phase.sub as Subscription;
  const heading =
    phase.kind === "confirm"
      ? "Confirm your subscription"
      : phase.kind === "activated"
        ? "Subscription active"
        : "Your marketplace subscription";

  return (
    <div className="mx-auto max-w-md">
      <Card>
        <h1 className="m-0 flex items-center gap-2 text-xl font-bold text-ink">
          {phase.kind !== "confirm" && (
            <BadgeCheck size={20} className="text-success" aria-hidden />
          )}
          {heading}
        </h1>
        {phase.kind === "confirm" && (
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Review what you purchased. Billing starts when you activate.
          </p>
        )}

        <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="font-semibold text-ink-muted">Plan</dt>
          <dd className="m-0 flex items-center gap-2 text-ink">
            <span className="font-bold capitalize">{sub.planKey}</span>
            {sub.freeTrial && <Badge tone="success">30-day free trial</Badge>}
          </dd>
          <dt className="font-semibold text-ink-muted">Status</dt>
          <dd className="m-0 text-ink">{sub.status}</dd>
          <dt className="font-semibold text-ink-muted">Billing term</dt>
          <dd className="m-0 text-ink">
            {sub.termStart || sub.termEnd
              ? `${fmtDate(sub.termStart)} → ${fmtDate(sub.termEnd)}`
              : "Starts at activation"}
          </dd>
          <dt className="font-semibold text-ink-muted">Account</dt>
          <dd className="m-0 break-all text-ink">{sub.beneficiaryEmail || "—"}</dd>
        </dl>

        {phase.kind === "confirm" && (
          <Button variant="primary" fullWidth className="mt-5" disabled={busy} onClick={onActivate}>
            {busy ? "Activating…" : "Activate subscription"}
          </Button>
        )}

        {phase.kind === "activated" && (
          <p className="mt-4 rounded-[10px] border border-success-border bg-success-bg p-3 text-sm text-success">
            You&apos;re all set — your plan is live and your API quota has been raised. Create an
            API key from the dashboard to start scanning.
          </p>
        )}

        <div className="mt-4 flex flex-wrap gap-2.5">
          {phase.kind !== "confirm" && (
            <ButtonLink href="/login" variant="primary" size="sm">
              Go to your dashboard
            </ButtonLink>
          )}
          <ButtonLink
            href="https://portal.azure.com/#browse/Microsoft.SaaS%2Fresources"
            variant="secondary"
            size="sm"
            target="_blank"
            rel="noopener noreferrer"
          >
            Manage in Azure <ExternalLink size={14} aria-hidden />
          </ButtonLink>
        </div>
      </Card>
    </div>
  );
}
