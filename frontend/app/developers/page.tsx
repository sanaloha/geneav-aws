import ScanForm from "../components/ScanForm";

export const metadata = {
  title: "Developers — API",
  description:
    "Integrate malware scanning with one HTTP call. POST a file to /api/v1/scan and get a JSON verdict. Open-source, OpenAPI-documented, powered by ClamAV — try a live scan.",
  alternates: { canonical: "/developers" },
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export default function Developers() {
  return (
    <section className="section">
      <div className="container">
        <h2>Developers</h2>
        <p className="lead">Integrate document scanning with a single HTTP call.</p>

        <h3>Try it now</h3>
        <p style={{ color: "var(--muted)" }}>
          Upload a document and see the live verdict. Requests go to{" "}
          <code>{API_BASE}</code>.
        </p>
        <ScanForm />

        <h3 style={{ marginTop: 40 }}>Scan a document</h3>
        <pre>
          <code>{`POST /api/v1/scan
Content-Type: multipart/form-data

field: file=<your document>`}</code>
        </pre>
        <p style={{ color: "var(--muted)" }}>Example:</p>
        <pre>
          <code>{`curl -F "file=@invoice.pdf" ${API_BASE}/api/v1/scan`}</code>
        </pre>
        <p style={{ color: "var(--muted)" }}>Response <code>200 OK</code>:</p>
        <pre>
          <code>{`{
  "scanId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "infected",
  "threat": "Eicar-Test-Signature",
  "fileName": "invoice.pdf",
  "fileSize": 68,
  "contentType": "application/pdf",
  "scannedAt": "2026-07-04T06:47:00Z"
}`}</code>
        </pre>

        <h3 style={{ marginTop: 32 }}>Errors</h3>
        <div className="grid">
          <div className="card"><h3>400</h3><p>No file provided or the file is empty.</p></div>
          <div className="card"><h3>413</h3><p>File exceeds the maximum allowed size (25&nbsp;MB).</p></div>
          <div className="card"><h3>415</h3><p>Unsupported content type.</p></div>
        </div>

        <h3 style={{ marginTop: 32 }}>Health</h3>
        <pre>
          <code>{`GET /api/v1/health  ->  { "status": "UP", "engine": "UP" }`}</code>
        </pre>

        <p style={{ marginTop: 24 }}>
          Full interactive reference:{" "}
          <a href={`${API_BASE}/docs`} target="_blank" rel="noreferrer">
            Swagger UI ({API_BASE}/docs)
          </a>
        </p>
      </div>
    </section>
  );
}
