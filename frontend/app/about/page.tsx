export const metadata = { title: "About — geneav" };

export default function About() {
  return (
    <section className="section">
      <div className="container">
        <h2>About geneav</h2>
        <p className="lead" style={{ maxWidth: 680 }}>
          geneav is a focused antivirus for documents. We believe scanning a file for malware
          should be as easy as making an HTTP request — so we wrapped a proven detection engine
          in a clean REST API and a simple website.
        </p>

        <div className="grid" style={{ marginTop: 24 }}>
          <div className="card">
            <h3>Our mission</h3>
            <p>Make malware scanning a one-call building block for any application.</p>
          </div>
          <div className="card">
            <h3>The engine</h3>
            <p>geneav is powered by ClamAV, an open-source antivirus engine with a broad, frequently updated signature set.</p>
          </div>
          <div className="card">
            <h3>Contact</h3>
            <p>
              Questions or feedback? Email{" "}
              <a href="mailto:admin@geneav.com">admin@geneav.com</a>.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
