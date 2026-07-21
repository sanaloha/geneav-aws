import { Activity, BookOpen, FileCheck2, Radio, ShieldCheck, SlidersHorizontal } from "lucide-react";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "Features",
  description:
    "How geneav scans documents for malware: the ClamAV engine, streaming INSTREAM scans, structured JSON verdicts, type and size guards, health checks, and OpenAPI docs.",
  alternates: { canonical: "/features" },
};

const features = [
  {
    icon: ShieldCheck,
    title: "ClamAV engine",
    body: "Documents are streamed to a ClamAV daemon and matched against its signature database.",
  },
  {
    icon: Radio,
    title: "Streaming scans",
    body: "Files are streamed over ClamAV's INSTREAM protocol — nothing is written to disk on the API.",
  },
  {
    icon: FileCheck2,
    title: "Structured verdicts",
    body: "Every scan returns clean/infected, the threat name, file metadata, and a unique scan id.",
  },
  {
    icon: SlidersHorizontal,
    title: "Type & size guards",
    body: "Unsupported types are rejected with 415, oversized files with 413 — before they hit the engine.",
  },
  {
    icon: Activity,
    title: "Health checks",
    body: "GET /api/v1/health reports whether the API and scan engine are ready.",
  },
  {
    icon: BookOpen,
    title: "OpenAPI docs",
    body: "Interactive Swagger UI ships with the API at /docs.",
  },
];

const documentTypes = ["PDF", "Word", "Excel", "PowerPoint", "RTF", "Plain text", "CSV", "ZIP"];

export default function Features() {
  return (
    <>
      <Section>
        <PageHeader title="Features" lead="What geneav does and how it works." />
        <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {features.map(({ icon: Icon, title, body }) => (
            <Card key={title} interactive>
              <Icon size={22} className="mb-3 text-brand" aria-hidden />
              <h2 className="m-0 text-lg font-bold text-ink">{title}</h2>
              <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">{body}</p>
            </Card>
          ))}
        </div>
      </Section>

      <Section tone="subtle">
        <PageHeader
          level="h2"
          title="Supported document types"
          lead="More can be enabled server-side as needed. The upload limit is 25 MB per file."
        />
        <ul className="m-0 flex list-none flex-wrap gap-2.5 p-0">
          {documentTypes.map((type) => (
            <li
              key={type}
              className="rounded-full border border-line bg-surface px-4 py-2 text-sm font-semibold text-ink-muted"
            >
              {type}
            </li>
          ))}
        </ul>
      </Section>
    </>
  );
}
