export const metadata = { title: "Features — geneav" };

export default function Features() {
  return (
    <section className="section">
      <div className="container">
        <h2>Features</h2>
        <p className="lead">What geneav does and how it works.</p>

        <div className="grid">
          <div className="card">
            <h3>ClamAV engine</h3>
            <p>Documents are streamed to a ClamAV daemon and matched against its signature database.</p>
          </div>
          <div className="card">
            <h3>Streaming scans</h3>
            <p>Files are streamed over ClamAV&apos;s INSTREAM protocol — nothing is written to disk on the API.</p>
          </div>
          <div className="card">
            <h3>Structured verdicts</h3>
            <p>Every scan returns clean/infected, the threat name, file metadata, and a unique scan id.</p>
          </div>
          <div className="card">
            <h3>Type &amp; size guards</h3>
            <p>Unsupported types are rejected with 415, oversized files with 413 — before they hit the engine.</p>
          </div>
          <div className="card">
            <h3>Health checks</h3>
            <p><code>GET /api/v1/health</code> reports whether the API and scan engine are ready.</p>
          </div>
          <div className="card">
            <h3>OpenAPI docs</h3>
            <p>Interactive Swagger UI ships with the API at <code>/docs</code>.</p>
          </div>
        </div>

        <h2 style={{ marginTop: 48 }}>Supported document types</h2>
        <p className="lead">
          PDF, Word, Excel, PowerPoint, RTF, plain text, CSV, and ZIP archives. More can be
          enabled server-side as needed.
        </p>
      </div>
    </section>
  );
}
