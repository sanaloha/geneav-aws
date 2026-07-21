"use client";

import clsx from "clsx";
import { AlertTriangle, CheckCircle2, Loader2, ShieldAlert, UploadCloud } from "lucide-react";
import { useCallback, useRef, useState } from "react";
import Card from "../ui/Card";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

type ScanResponse = {
  scanId: string;
  status: "clean" | "infected";
  threat: string | null;
  fileName: string;
  fileSize: number;
  contentType: string | null;
  scannedAt: string;
};

type State =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "done"; data: ScanResponse }
  | { kind: "error"; message: string };

function Row({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className="text-ink-muted">{label}</dt>
      <dd className="m-0 break-all font-medium text-ink">{value}</dd>
    </>
  );
}

export default function ScanForm() {
  const [state, setState] = useState<State>({ kind: "idle" });
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const upload = useCallback(async (file: File) => {
    setState({ kind: "loading" });
    try {
      const body = new FormData();
      body.append("file", file);
      const res = await fetch(`${API_BASE}/api/v1/scan`, { method: "POST", body });
      if (!res.ok) {
        let message = `Scan failed (HTTP ${res.status}).`;
        try {
          const err = await res.json();
          if (err?.message) message = err.message;
        } catch {
          /* non-JSON error body */
        }
        setState({ kind: "error", message });
        return;
      }
      const data: ScanResponse = await res.json();
      setState({ kind: "done", data });
    } catch {
      setState({
        kind: "error",
        message:
          "Could not reach the scan API. Make sure the backend is running at " + API_BASE + ".",
      });
    }
  }, []);

  const onFiles = (files: FileList | null) => {
    if (files && files.length > 0) upload(files[0]);
  };

  return (
    <div>
      <div
        className={clsx(
          "cursor-pointer rounded-card border-[1.5px] border-dashed p-10 text-center transition-colors",
          "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand",
          dragging ? "border-brand bg-brand-bg" : "border-line-control bg-surface-subtle hover:border-brand"
        )}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          onFiles(e.dataTransfer.files);
        }}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          // The div is role="button", so it must respond to keyboard activation.
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            inputRef.current?.click();
          }
        }}
        role="button"
        tabIndex={0}
      >
        <UploadCloud size={32} className="mx-auto mb-3 text-brand" aria-hidden />
        <p className="m-0 font-semibold text-ink">
          Drop a document here, or <span className="text-brand underline">browse</span>
        </p>
        <p className="mt-1.5 text-sm text-ink-muted">
          PDF, Office docs, text, ZIP · up to 25&nbsp;MB
        </p>
        <input ref={inputRef} type="file" hidden onChange={(e) => onFiles(e.target.files)} />
      </div>

      <div aria-live="polite">
        {state.kind === "loading" && (
          <Card className="mt-5">
            <h3 className="m-0 flex items-center gap-2.5 text-base font-bold text-ink">
              <Loader2 size={18} className="animate-spin text-brand" aria-hidden />
              Scanning…
            </h3>
          </Card>
        )}

        {state.kind === "error" && (
          <Card tone="warning" className="mt-5">
            <h3 className="m-0 flex items-center gap-2.5 text-base font-bold text-warning">
              <AlertTriangle size={18} aria-hidden />
              Couldn&apos;t scan
            </h3>
            <p className="mt-2 text-[15px] text-ink-muted">{state.message}</p>
          </Card>
        )}

        {state.kind === "done" && (
          <Card tone={state.data.status === "clean" ? "success" : "danger"} className="mt-5">
            <h3
              className={clsx(
                "m-0 flex items-center gap-2.5 text-base font-extrabold",
                state.data.status === "clean" ? "text-success" : "text-danger"
              )}
            >
              {state.data.status === "clean" ? (
                <CheckCircle2 size={20} aria-hidden />
              ) : (
                <ShieldAlert size={20} aria-hidden />
              )}
              {/* Verdict stays as text so it reads correctly without the icon. */}
              Verdict: {state.data.status === "clean" ? "CLEAN" : "INFECTED"}
            </h3>
            <dl className="mt-4 grid grid-cols-1 gap-x-4 gap-y-1 text-sm sm:grid-cols-[140px_1fr]">
              {state.data.threat && <Row label="Threat" value={state.data.threat} />}
              <Row label="File" value={state.data.fileName} />
              <Row label="Size" value={`${state.data.fileSize.toLocaleString()} bytes`} />
              <Row label="Type" value={state.data.contentType ?? "—"} />
              <Row label="Scan id" value={state.data.scanId} />
              <Row label="Scanned at" value={state.data.scannedAt} />
            </dl>
          </Card>
        )}
      </div>
    </div>
  );
}
