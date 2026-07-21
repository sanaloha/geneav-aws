"use client";

import { AlertTriangle, CheckCircle2, LinkIcon } from "lucide-react";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Button, ButtonLink } from "../ui/Button";
import Card from "../ui/Card";
import { Field, Input } from "../ui/Field";

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
      <div className="mx-auto max-w-md">
        <Card>
          <CheckCircle2 size={28} className="mb-3 text-success" aria-hidden />
          <h2 className="m-0 text-xl font-bold text-ink">Password updated</h2>
          <p className="mb-6 mt-2 text-[15px] leading-relaxed text-ink-muted">
            Your password has been changed and a confirmation email is on its way.
          </p>
          <ButtonLink href="/login" variant="primary" fullWidth>
            Sign in
          </ButtonLink>
        </Card>
      </div>
    );
  }

  if (token === null) {
    return (
      <div className="mx-auto max-w-md">
        <Card>
          <LinkIcon size={28} className="mb-3 text-warning" aria-hidden />
          <h2 className="m-0 text-xl font-bold text-ink">Reset link missing</h2>
          <p className="mb-6 mt-2 text-[15px] leading-relaxed text-ink-muted">
            This page needs the link from your reset email. Open that link, or request a new one —
            links expire 10 minutes after they are sent.
          </p>
          <ButtonLink href="/login" variant="secondary" fullWidth>
            Back to sign in
          </ButtonLink>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md">
      <Card>
        <h2 className="m-0 text-xl font-bold text-ink">Choose a new password</h2>

        {error && (
          <div
            role="alert"
            className="mt-4 flex items-start gap-2.5 rounded-[10px] border border-danger-border bg-danger-bg p-3 text-sm text-danger"
          >
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
            {error}
          </div>
        )}

        <form onSubmit={onSubmit} className="mt-5">
          <Field
            label="New password"
            hint="At least 12 characters, mixing three of: lowercase, uppercase, digits, symbols."
          >
            <Input
              type="password"
              required
              minLength={12}
              autoComplete="new-password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>
          <Field label="Confirm new password">
            <Input
              type="password"
              required
              autoComplete="new-password"
              placeholder="••••••••"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </Field>
          <Button type="submit" variant="primary" fullWidth disabled={busy} className="mt-1">
            {busy ? "Saving…" : "Set new password"}
          </Button>
        </form>
      </Card>
    </div>
  );
}
