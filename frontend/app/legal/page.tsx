export const metadata = {
  title: "Legal",
  description:
    "Legal and attribution for geneav — proprietary terms plus open-source credit for the ClamAV detection engine (GNU GPL v2).",
  alternates: { canonical: "/legal" },
};

export default function Legal() {
  const year = new Date().getFullYear();
  return (
    <section className="section">
      <div className="container">
        <h2>Legal &amp; attribution</h2>
        <p className="lead" style={{ maxWidth: 680 }}>
          geneav is a proprietary product built on open-source foundations. This
          page summarizes geneav&apos;s own terms and credits the third-party
          software that powers it.
        </p>

        <div className="grid" style={{ marginTop: 24 }}>
          <div className="card">
            <h3>geneav</h3>
            <p>
              The geneav website and API are proprietary. © {year} Santosh Singh —
              all rights reserved. No permission to copy, modify, or redistribute
              geneav&apos;s code is granted except under a separate written
              agreement.
            </p>
          </div>
          <div className="card">
            <h3>ClamAV (open source)</h3>
            <p>
              geneav&apos;s detection engine is{" "}
              <a
                href="https://github.com/Cisco-Talos/clamav"
                target="_blank"
                rel="noopener noreferrer"
              >
                ClamAV
              </a>
              , licensed under the{" "}
              <a
                href="https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
                target="_blank"
                rel="noopener noreferrer"
              >
                GNU GPL v2
              </a>
              . geneav communicates with the ClamAV daemon (clamd) over a network
              socket using its INSTREAM protocol and does not link{" "}
              <code>libclamav</code> — so the GPL applies to ClamAV itself and not
              to geneav&apos;s own code. ClamAV is a trademark of Cisco Systems,
              Inc.
            </p>
          </div>
          <div className="card">
            <h3>Contact</h3>
            <p>
              Licensing questions or feedback? Email{" "}
              <a href="mailto:admin@geneav.com">admin@geneav.com</a>.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
