import { Mail, Scale, ShieldCheck } from "lucide-react";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "Legal",
  description:
    "Legal and attribution for geneav — proprietary terms plus open-source credit for the ClamAV detection engine (GNU GPL v2).",
  alternates: { canonical: "/legal" },
};

const linkClass = "font-semibold text-brand";

export default function Legal() {
  const year = new Date().getFullYear();
  return (
    <Section>
      <PageHeader
        title="Legal & attribution"
        lead="geneav is a proprietary product built on open-source foundations. This page summarizes geneav's own terms and credits the third-party software that powers it."
      />

      {/* The ClamAV notice is several times longer than the others, so this is a
          two-column layout rather than an even three — a 3-up grid sized to one
          sentence made that card overflow awkwardly. */}
      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <Scale size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">geneav</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            The geneav website and API are proprietary. © {year} Santosh Singh — all rights
            reserved. No permission to copy, modify, or redistribute geneav&apos;s code is granted
            except under a separate written agreement.
          </p>
        </Card>

        <Card>
          <ShieldCheck size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">ClamAV (open source)</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav&apos;s detection engine is{" "}
            <a
              href="https://github.com/Cisco-Talos/clamav"
              target="_blank"
              rel="noopener noreferrer"
              className={linkClass}
            >
              ClamAV
            </a>
            , licensed under the{" "}
            <a
              href="https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
              target="_blank"
              rel="noopener noreferrer"
              className={linkClass}
            >
              GNU GPL v2
            </a>
            . geneav communicates with the ClamAV daemon (clamd) over a network socket using its
            INSTREAM protocol and does not link <code className="text-ink">libclamav</code> — so
            the GPL applies to ClamAV itself and not to geneav&apos;s own code. ClamAV is a
            trademark of Cisco Systems, Inc.
          </p>
        </Card>

        <Card className="lg:col-span-2">
          <Mail size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Contact</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Licensing questions or feedback? Email{" "}
            <a href="mailto:admin@geneav.com" className={linkClass}>
              admin@geneav.com
            </a>
            .
          </p>
        </Card>
      </div>
    </Section>
  );
}
