"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";

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

export default function Dashboard() {
  const [authState, setAuthState] = useState<"loading" | "anon" | "authed">("loading");
  const [me, setMe] = useState<Me | null>(null);

  // Landing (sign in / sign up) form
  const [mode, setMode] = useState<"signin" | "signup">("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

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
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/${mode === "signup" ? "signup" : "login"}`, {
          ...withCreds,
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
        });
        if (!res.ok) {
          setAuthError(await apiErrorMessage(res, mode === "signup" ? "Sign up failed" : "Sign in failed"));
          return;
        }
        setMe(await res.json());
        setPassword("");
        setAuthState("authed");
      } catch {
        setAuthError(`Could not reach the API at ${API_BASE}.`);
      } finally {
        setAuthBusy(false);
      }
    },
    [mode, email, password]
  );

  const signOut = useCallback(async () => {
    try {
      await fetch(`${API_BASE}/api/v1/auth/logout`, { ...withCreds, method: "POST" });
    } catch {
      /* ignore */
    }
    setMe(null);
    setUsage(null);
    setKeys(null);
    setRevealed(null);
    setAuthState("anon");
  }, []);

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
    return <p style={{ color: "var(--muted)" }}>Loading…</p>;
  }

  // ---- Signed out: sign in / sign up --------------------------------------
  if (authState === "anon") {
    return (
      <div className="auth-wrap">
        <div className="card">
          <div className="auth-tabs">
            <button
              className={`auth-tab${mode === "signin" ? " active" : ""}`}
              onClick={() => {
                setMode("signin");
                setAuthError(null);
              }}
            >
              Sign in
            </button>
            <button
              className={`auth-tab${mode === "signup" ? " active" : ""}`}
              onClick={() => {
                setMode("signup");
                setAuthError(null);
              }}
            >
              Sign up
            </button>
          </div>

          {authError && (
            <div className="result error" style={{ marginTop: 0, marginBottom: 14 }}>
              <p style={{ margin: 0 }}>{authError}</p>
            </div>
          )}

          <form onSubmit={onAuth}>
            <label className="field">
              <span>Email</span>
              <input
                className="input"
                type="email"
                required
                autoComplete="email"
                placeholder="you@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
            <label className="field">
              <span>Password{mode === "signup" ? " (at least 8 characters)" : ""}</span>
              <input
                className="input"
                type="password"
                required
                minLength={mode === "signup" ? 8 : undefined}
                autoComplete={mode === "signup" ? "new-password" : "current-password"}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <button className="btn btn-primary" type="submit" disabled={authBusy} style={{ width: "100%" }}>
              {authBusy ? "Please wait…" : mode === "signup" ? "Create account" : "Sign in"}
            </button>
          </form>

          <div className="auth-divider"><span>or</span></div>

          <button className="btn btn-ghost" style={{ width: "100%" }} disabled title="Available soon">
            <span aria-hidden style={{ marginRight: 8 }}>G</span> Continue with Google
            <span className="muted-sm" style={{ marginLeft: 8 }}>(soon)</span>
          </button>
        </div>
      </div>
    );
  }

  // ---- Signed in ----------------------------------------------------------
  const pct =
    usage && usage.scansQuota > 0
      ? Math.min(100, Math.round((usage.scansUsed / usage.scansQuota) * 100))
      : 0;

  return (
    <div>
      {revealed && (
        <div className="reveal">
          <h4 style={{ margin: "0 0 6px" }}>Save your API key now</h4>
          <p style={{ margin: "0 0 12px", color: "var(--muted)", fontSize: 14 }}>
            This is the only time it will be shown. Store it somewhere safe.
          </p>
          <div className="reveal-row">
            <code className="reveal-key">{revealed}</code>
            <button className="btn btn-primary btn-sm" onClick={copyRevealed}>
              {copied ? "Copied ✓" : "Copy"}
            </button>
          </div>
          <button className="linklike" onClick={() => setRevealed(null)}>
            I&apos;ve saved it — dismiss
          </button>
        </div>
      )}

      <div className="dash-head">
        <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
          {usage && <span className="badge">{usage.plan} plan</span>}
          {me && <span className="muted-sm">{me.email}</span>}
        </div>
        <button className="btn btn-ghost btn-sm" onClick={signOut}>
          Sign out
        </button>
      </div>

      {dataError && (
        <div className="result error">
          <p style={{ margin: 0 }}>{dataError}</p>
        </div>
      )}

      <div className="card" style={{ marginBottom: 18 }}>
        <h3>Usage this month {usage && <span className="muted-sm">· {usage.period}</span>}</h3>
        {loading && !usage ? (
          <p style={{ color: "var(--muted)", margin: 0 }}>Loading…</p>
        ) : usage ? (
          <>
            <p style={{ margin: "4px 0 12px" }}>
              <strong style={{ fontSize: 22 }}>{usage.scansUsed.toLocaleString()}</strong>{" "}
              <span style={{ color: "var(--muted)" }}>
                / {usage.scansQuota.toLocaleString()} scans · {usage.scansRemaining.toLocaleString()} left
              </span>
            </p>
            <div className="meter">
              <div className={`meter-fill${pct >= 100 ? " full" : ""}`} style={{ width: `${pct}%` }} />
            </div>
          </>
        ) : null}
      </div>

      <div className="card">
        <h3>API keys</h3>
        <p className="muted-sm" style={{ margin: "0 0 12px" }}>
          Use a key as <code>Authorization: Bearer …</code> to call the API.
        </p>
        <form onSubmit={onCreateKey} className="keyform">
          <input
            className="input"
            placeholder="Key name (e.g. production)"
            value={newKeyName}
            onChange={(e) => setNewKeyName(e.target.value)}
          />
          <button className="btn btn-primary btn-sm" type="submit">
            Create key
          </button>
        </form>

        {keys && keys.length > 0 ? (
          <div className="keylist">
            {keys.map((k) => (
              <div className="keyrow" key={k.id}>
                <div className="keyrow-main">
                  <code>
                    {k.keyPrefix}…{k.lastFour}
                  </code>
                  <span className={`keystatus ${k.status}`}>{k.status}</span>
                  <span className="keyname">{k.name || "—"}</span>
                </div>
                <div className="keyrow-meta">
                  <span>created {fmtDate(k.createdAt)}</span>
                  <span>last used {fmtDate(k.lastUsedAt)}</span>
                </div>
                <div className="keyrow-action">
                  {k.status !== "active" ? (
                    <span className="muted-sm">revoked</span>
                  ) : confirmRevoke === k.id ? (
                    <>
                      <button className="btn btn-danger btn-sm" onClick={() => onRevoke(k.id)}>
                        Confirm
                      </button>
                      <button className="linklike" onClick={() => setConfirmRevoke(null)}>
                        Cancel
                      </button>
                    </>
                  ) : (
                    <button className="linklike danger" onClick={() => setConfirmRevoke(k.id)}>
                      Revoke
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p style={{ color: "var(--muted)", margin: "8px 0 0" }}>
            {loading ? "Loading…" : "No keys yet — create one above."}
          </p>
        )}
      </div>
    </div>
  );
}
