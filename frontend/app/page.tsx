import Link from "next/link";
import { REPO_URL, LICENSE_URL } from "./site";

export default function Home() {
  return (
    <>
      <section className="hero">
        <div className="container">
          <div className="badge-row">
            <span className="badge">Open source · MIT</span>
            <span className="badge">Powered by ClamAV</span>
          </div>
          <h1>Scan every document for malware.</h1>
          <p>
            geneav is a free, open-source antivirus built for documents. Drop in a file and get
            an instant clean-or-infected verdict — from our website or straight from your code
            via a simple REST API.
          </p>
          <div className="btn-row">
            <Link href="/developers" className="btn btn-primary">
              Try a scan
            </Link>
            <Link href="/features" className="btn btn-ghost">
              How it works
            </Link>
            <a href={REPO_URL} className="btn btn-ghost" target="_blank" rel="noreferrer">
              ★ View on GitHub
            </a>
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
            <div className="card">
              <h3>🔓 Open source</h3>
              <p>
                MIT licensed and self-hostable. Audit every line, run your own instance, or
                contribute — no black boxes, no lock-in.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="os-panel">
            <span className="badge">🔓 Free &amp; open source</span>
            <h2>Open source, MIT licensed</h2>
            <p className="lead">
              geneav is fully open source. Read the code, self-host it on your own
              infrastructure, or send a pull request. Transparency is a security feature —
              you never have to trust a black box with your files.
            </p>
            <div className="btn-row btn-row-left">
              <a href={REPO_URL} className="btn btn-primary" target="_blank" rel="noreferrer">
                ★ Star on GitHub
              </a>
              <a href={LICENSE_URL} className="btn btn-ghost" target="_blank" rel="noreferrer">
                Read the MIT License
              </a>
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <h2>From upload to verdict in one call</h2>
          <p className="lead">Send a document, get a structured result.</p>
          <pre>
            <code>{`curl -F "file=@invoice.pdf" https://geneav.com/api/v1/scan

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
