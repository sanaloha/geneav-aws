"use client";

import { useCallback, useRef, useState } from "react";

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
    } catch (e) {
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
        className={`dropzone${dragging ? " drag" : ""}`}
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
        role="button"
        tabIndex={0}
      >
        <p style={{ margin: 0, fontWeight: 600 }}>
          Drop a document here, or <span style={{ color: "var(--brand)" }}>browse</span>
        </p>
        <p style={{ margin: "6px 0 0", color: "var(--muted)", fontSize: 14 }}>
          PDF, Office docs, text, ZIP · up to 25&nbsp;MB
        </p>
        <input
          ref={inputRef}
          type="file"
          hidden
          onChange={(e) => onFiles(e.target.files)}
        />
      </div>

      {state.kind === "loading" && (
        <div className="result" style={{ marginTop: 20 }}>
          <h4>Scanning…</h4>
        </div>
      )}

      {state.kind === "error" && (
        <div className="result error">
          <h4>Couldn&apos;t scan</h4>
          <p style={{ margin: 0, color: "var(--muted)" }}>{state.message}</p>
        </div>
      )}

      {state.kind === "done" && (
        <div className={`result ${state.data.status}`}>
          <h4>
            Verdict:{" "}
            <span className={`tag ${state.data.status}`}>
              {state.data.status === "clean" ? "CLEAN ✓" : "INFECTED ✕"}
            </span>
          </h4>
          <dl className="kv">
            {state.data.threat && (
              <>
                <dt>Threat</dt>
                <dd>{state.data.threat}</dd>
              </>
            )}
            <dt>File</dt>
            <dd>{state.data.fileName}</dd>
            <dt>Size</dt>
            <dd>{state.data.fileSize.toLocaleString()} bytes</dd>
            <dt>Type</dt>
            <dd>{state.data.contentType ?? "—"}</dd>
            <dt>Scan id</dt>
            <dd>{state.data.scanId}</dd>
            <dt>Scanned at</dt>
            <dd>{state.data.scannedAt}</dd>
          </dl>
        </div>
      )}
    </div>
  );
}
