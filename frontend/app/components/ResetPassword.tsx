"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

async function apiErrorMessage(res: Response, fallback: string): Promise<string> {
  try {
    const body = await res.json();
    if (body?.message) return body.message as string;
  } catch {
    /* non-JSON body */
  }
  return `${fallback} (HTTP ${res.status}).`;
}

export default function ResetPassword() {
  const [token, setToken] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  // The token arrives in the query string of the emailed link. Read it client
  // side so the page itself can stay static.
  useEffect(() => {
    const value = new URLSearchParams(window.location.search).get("token");
    setToken(value && value.trim() !== "" ? value : null);
  }, []);

  const onSubmit = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      if (password !== confirm) {
        setError("The two passwords do not match.");
        return;
      }
      setBusy(true);
      setError(null);
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/reset-password`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token, password }),
        });
        if (!res.ok) {
          // Covers an expired, already-used, or unknown token as well as a
          // password that fails the strength policy — the API says which.
          setError(await apiErrorMessage(res, "Could not reset your password"));
          return;
        }
        setDone(true);
        setPassword("");
        setConfirm("");
      } catch {
        setError(`Could not reach the API at ${API_BASE}.`);
      } finally {
        setBusy(false);
      }
    },
    [token, password, confirm]
  );

  if (done) {
    return (
      <div className="auth-wrap">
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Password updated</h3>
          <p style={{ color: "var(--muted)" }}>
            Your password has been changed and a confirmation email is on its way.
          </p>
          <a className="btn btn-primary" href="/login" style={{ width: "100%" }}>
            Sign in
          </a>
        </div>
      </div>
    );
  }

  if (token === null) {
    return (
      <div className="auth-wrap">
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Reset link missing</h3>
          <p style={{ color: "var(--muted)" }}>
            This page needs the link from your reset email. Open that link, or request a new
            one — links expire 10 minutes after they are sent.
          </p>
          <a className="btn btn-ghost" href="/login" style={{ width: "100%" }}>
            Back to sign in
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-wrap">
      <div className="card">
        <h3 style={{ marginTop: 0 }}>Choose a new password</h3>

        {error && (
          <div className="result error" style={{ marginTop: 0, marginBottom: 14 }}>
            <p style={{ margin: 0 }}>{error}</p>
          </div>
        )}

        <form onSubmit={onSubmit}>
          <label className="field">
            <span>New password</span>
            <input
              className="input"
              type="password"
              required
              minLength={12}
              autoComplete="new-password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <span className="muted-sm">
              At least 12 characters, mixing three of: lowercase, uppercase, digits, symbols.
            </span>
          </label>
          <label className="field">
            <span>Confirm new password</span>
            <input
              className="input"
              type="password"
              required
              autoComplete="new-password"
              placeholder="••••••••"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </label>
          <button className="btn btn-primary" type="submit" disabled={busy} style={{ width: "100%" }}>
            {busy ? "Saving…" : "Set new password"}
          </button>
        </form>
      </div>
    </div>
  );
}
