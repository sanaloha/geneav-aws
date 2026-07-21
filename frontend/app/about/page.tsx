import { Mail, ShieldCheck, Target } from "lucide-react";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "About",
  description:
    "geneav is a focused antivirus for documents — malware scanning as simple as an HTTP request, powered by ClamAV.",
  alternates: { canonical: "/about" },
};

export default function About() {
  return (
    <Section>
      <PageHeader
        title="About geneav"
        lead="geneav is a focused antivirus for documents. We believe scanning a file for malware should be as easy as making an HTTP request — so we wrapped a proven detection engine in a clean REST API and a simple website."
      />

      <div className="grid gap-5 md:grid-cols-3">
        <Card>
          <Target size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Our mission</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Make malware scanning a one-call building block for any application.
          </p>
        </Card>
        <Card>
          <ShieldCheck size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">The engine</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav is powered by ClamAV, a widely used antivirus engine with a broad, frequently
            updated signature set.
          </p>
        </Card>
        <Card>
          <Mail size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Contact</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Questions or feedback? Email{" "}
            <a href="mailto:admin@geneav.com" className="font-semibold text-brand">
              admin@geneav.com
            </a>
            .
          </p>
        </Card>
      </div>
    </Section>
  );
}
