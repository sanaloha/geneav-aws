"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const STORAGE_KEY = "geneav.apiKey";

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
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [ready, setReady] = useState(false); // hydrated from sessionStorage yet?

  // Landing form state
  const [email, setEmail] = useState("");
  const [pasteKey, setPasteKey] = useState("");
  const [landingBusy, setLandingBusy] = useState(false);
  const [landingError, setLandingError] = useState<string | null>(null);

  // Dashboard data
  const [usage, setUsage] = useState<Usage | null>(null);
  const [keys, setKeys] = useState<KeySummary[] | null>(null);
  const [dataError, setDataError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // One-time plaintext key reveal (after signup or create)
  const [revealed, setRevealed] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const [newKeyName, setNewKeyName] = useState("");
  const [confirmRevoke, setConfirmRevoke] = useState<string | null>(null);

  useEffect(() => {
    setApiKey(sessionStorage.getItem(STORAGE_KEY));
    setReady(true);
  }, []);

  const persistKey = useCallback((key: string) => {
    sessionStorage.setItem(STORAGE_KEY, key);
    setApiKey(key);
  }, []);

  const signOut = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY);
    setApiKey(null);
    setUsage(null);
    setKeys(null);
    setRevealed(null);
    setDataError(null);
  }, []);

  const loadData = useCallback(async (key: string) => {
    setLoading(true);
    setDataError(null);
    try {
      const [uRes, kRes] = await Promise.all([
        fetch(`${API_BASE}/api/v1/usage`, { headers: { Authorization: `Bearer ${key}` } }),
        fetch(`${API_BASE}/api/v1/keys`, { headers: { Authorization: `Bearer ${key}` } }),
      ]);
      if (uRes.status === 401 || kRes.status === 401) {
        sessionStorage.removeItem(STORAGE_KEY);
        setApiKey(null);
        setLandingError("That API key is invalid or was revoked. Please sign in again.");
        return;
      }
      if (!uRes.ok) {
        setDataError(await apiErrorMessage(uRes, "Could not load usage"));
        return;
      }
      setUsage(await uRes.json());
      setKeys(kRes.ok ? await kRes.json() : []);
    } catch {
      setDataError(`Could not reach the API at ${API_BASE}.`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (apiKey) loadData(apiKey);
  }, [apiKey, loadData]);

  const onSignup = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setLandingBusy(true);
      setLandingError(null);
      try {
        const res = await fetch(`${API_BASE}/api/v1/signup`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email }),
        });
        if (!res.ok) {
          setLandingError(await apiErrorMessage(res, "Sign up failed"));
          return;
        }
        const body = await res.json();
        setRevealed(body.apiKey);
        persistKey(body.apiKey);
      } catch {
        setLandingError(`Could not reach the API at ${API_BASE}.`);
      } finally {
        setLandingBusy(false);
      }
    },
    [email, persistKey]
  );

  const onUseExisting = useCallback(
    (e: FormEvent) => {
      e.preventDefault();
      const trimmed = pasteKey.trim();
      if (!trimmed) return;
      setLandingError(null);
      persistKey(trimmed); // validity is checked by the ensuing data load
    },
    [pasteKey, persistKey]
  );

  const onCreateKey = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      if (!apiKey) return;
      try {
        const res = await fetch(`${API_BASE}/api/v1/keys`, {
          method: "POST",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
          body: JSON.stringify({ name: newKeyName.trim() || "key" }),
        });
        if (!res.ok) {
          setDataError(await apiErrorMessage(res, "Could not create key"));
          return;
        }
        const body = await res.json();
        setRevealed(body.apiKey);
        setNewKeyName("");
        loadData(apiKey);
      } catch {
        setDataError(`Could not reach the API at ${API_BASE}.`);
      }
    },
    [apiKey, newKeyName, loadData]
  );

  const onRevoke = useCallback(
    async (id: string) => {
      if (!apiKey) return;
      setConfirmRevoke(null);
      try {
        const res = await fetch(`${API_BASE}/api/v1/keys/${id}`, {
          method: "DELETE",
          headers: { Authorization: `Bearer ${apiKey}` },
        });
        if (!res.ok && res.status !== 404) {
          setDataError(await apiErrorMessage(res, "Could not revoke key"));
          return;
        }
        loadData(apiKey);
      } catch {
        setDataError(`Could not reach the API at ${API_BASE}.`);
      }
    },
    [apiKey, loadData]
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

  if (!ready) return null; // avoid a hydration flash before we know if a key is stored

  // ---- Landing (no key) ----------------------------------------------------
  if (!apiKey) {
    return (
      <div>
        {landingError && (
          <div className="result error" style={{ marginTop: 0 }}>
            <p style={{ margin: 0 }}>{landingError}</p>
          </div>
        )}
        <div className="dash-cols">
          <div className="card">
            <h3>Create an account</h3>
            <p style={{ marginBottom: 16 }}>
              Get an API key on the free plan — 100 scans/month, no card required.
            </p>
            <form onSubmit={onSignup}>
              <label className="field">
                <span>Email</span>
                <input
                  className="input"
                  type="email"
                  required
                  placeholder="you@company.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </label>
              <button className="btn btn-primary" type="submit" disabled={landingBusy}>
                {landingBusy ? "Creating…" : "Create account & get key"}
              </button>
            </form>
          </div>

          <div className="card">
            <h3>Already have a key?</h3>
            <p style={{ marginBottom: 16 }}>
              Paste an existing <code>gav_live_…</code> key to view its usage and manage keys.
            </p>
            <form onSubmit={onUseExisting}>
              <label className="field">
                <span>API key</span>
                <input
                  className="input"
                  type="password"
                  placeholder="gav_live_…"
                  value={pasteKey}
                  onChange={(e) => setPasteKey(e.target.value)}
                />
              </label>
              <button className="btn btn-ghost" type="submit">
                Open dashboard
              </button>
            </form>
          </div>
        </div>
      </div>
    );
  }

  // ---- Dashboard (key present) --------------------------------------------
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
        <div>
          {usage && <span className="badge">{usage.plan} plan</span>}
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
              <div
                className={`meter-fill${pct >= 100 ? " full" : ""}`}
                style={{ width: `${pct}%` }}
              />
            </div>
          </>
        ) : null}
      </div>

      <div className="card">
        <h3>API keys</h3>
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
            {loading ? "Loading…" : "No keys yet."}
          </p>
        )}
      </div>
    </div>
  );
}
