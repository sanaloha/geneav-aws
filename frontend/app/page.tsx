import Link from "next/link";

export default function Home() {
  return (
    <>
      <section className="hero">
        <div className="container">
          <div className="badge-row">
            <span className="badge">Powered by ClamAV</span>
          </div>
          <h1>Scan every document for malware.</h1>
          <p>
            geneav is an antivirus built for documents. Drop in a file and get
            an instant clean-or-infected verdict — from our website or straight
            from your code via a simple REST API.
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
                Backed by the ClamAV engine and its constantly updated signature
                database — the same technology trusted across the industry.
              </p>
            </div>
            <div className="card">
              <h3>⚡ One endpoint</h3>
              <p>
                <code>POST /api/v1/scan</code> with a file. Get back a JSON
                verdict with the threat name, file metadata, and a scan id.
              </p>
            </div>
            <div className="card">
              <h3>🔌 Drop-in ready</h3>
              <p>
                Language-agnostic HTTP API and OpenAPI docs. Wire it into
                uploads, inboxes, or pipelines in minutes.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <span className="badge">🚧 Coming soon</span>
          <h2>What&apos;s next for geneav</h2>
          <p className="lead">
            Features we&apos;re building. Not available yet — everything below
            is planned work.
          </p>
          <div className="roadmap-grid">
            <div className="card roadmap-card">
              <div className="roadmap-head">
                <span className="roadmap-num">1</span>
                <h3>Customized corporate use</h3>
                <span className="roadmap-tag">Next</span>
              </div>
              <p>Login and create your API key to start using geneav.</p>
              <ul className="roadmap-list">
                <li>Unlimited free usage</li>
                <li>Dedicated APIs for virus scanning</li>
                <li>Secure, encrypted workspace</li>
                <li>High availability</li>
                <li>Advanced vulnerability checks</li>
              </ul>
            </div>
            <div className="card roadmap-card">
              <div className="roadmap-head">
                <span className="roadmap-num">2</span>
                <h3>geneav chat agent</h3>
                <span className="roadmap-tag">Planned</span>
              </div>
              <p>
                Ask about scan results and threats in plain language, and get
                help wiring the API into your own application.
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
