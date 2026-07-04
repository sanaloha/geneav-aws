import Link from "next/link";

export default function Home() {
  return (
    <>
      <section className="hero">
        <div className="container">
          <div className="badge">Powered by ClamAV</div>
          <h1>Scan every document for malware.</h1>
          <p>
            geneav is an antivirus built for documents. Drop in a file and get an instant
            clean-or-infected verdict — from our website or straight from your code via a
            simple REST API.
          </p>
          <div className="btn-row">
            <Link href="/developers" className="btn btn-primary">
              Try a scan
            </Link>
            <Link href="/features" className="btn btn-ghost">
              How it works
            </Link>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="grid">
            <div className="card">
              <h3>🛡️ Real detection</h3>
              <p>
                Backed by the ClamAV engine and its constantly updated signature database —
                the same technology trusted across the industry.
              </p>
            </div>
            <div className="card">
              <h3>⚡ One endpoint</h3>
              <p>
                <code>POST /api/v1/scan</code> with a file. Get back a JSON verdict with the
                threat name, file metadata, and a scan id.
              </p>
            </div>
            <div className="card">
              <h3>🔌 Drop-in ready</h3>
              <p>
                Language-agnostic HTTP API and OpenAPI docs. Wire it into uploads, inboxes,
                or pipelines in minutes.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <h2>From upload to verdict in one call</h2>
          <p className="lead">Send a document, get a structured result.</p>
          <pre>
            <code>{`curl -F "file=@invoice.pdf" http://localhost:8080/api/v1/scan

{
  "scanId": "a1b2c3d4-...",
  "status": "clean",
  "threat": null,
  "fileName": "invoice.pdf",
  "fileSize": 48213,
  "contentType": "application/pdf",
  "scannedAt": "2026-07-04T06:47:00Z"
}`}</code>
          </pre>
        </div>
      </section>
    </>
  );
}
