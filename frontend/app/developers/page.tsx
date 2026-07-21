import { ExternalLink } from "lucide-react";
import ScanForm from "../components/ScanForm";
import Badge from "../ui/Badge";
import { ButtonLink } from "../ui/Button";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "Developers — API",
  description:
    "Integrate malware scanning with one HTTP call. POST a file to /api/v1/scan and get a JSON verdict. OpenAPI-documented, powered by ClamAV — try a live scan.",
  alternates: { canonical: "/developers" },
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

const codeClass =
  "m-0 overflow-x-auto rounded-card border border-line bg-surface-sunken p-4 text-[13px] leading-relaxed";

const errors = [
  { code: "400", body: "No file provided or the file is empty." },
  { code: "413", body: "File exceeds the maximum allowed size (25 MB)." },
  { code: "415", body: "Unsupported content type." },
  { code: "402", body: "Monthly scan quota exhausted for this API key." },
  { code: "429", body: "Rate limit exceeded. Retry after the interval in the header." },
  { code: "503", body: "Scan engine unavailable." },
];

export default function Developers() {
  return (
    <>
      <Section>
        <PageHeader
          title="Developers"
          lead="Integrate document scanning with a single HTTP call. No SDK — it is just HTTP."
        />

        <h2 className="mb-2 mt-2 text-xl font-bold text-ink">Try it now</h2>
        <p className="mb-4 text-[15px] text-ink-muted">
          Upload a document and see the live verdict. Requests go to{" "}
          <code className="rounded bg-surface-sunken px-1.5 py-0.5 text-[13px] text-ink">
            {API_BASE}
          </code>
          .
        </p>
        <ScanForm />
      </Section>

      <Section tone="subtle">
        <h2 className="m-0 text-xl font-bold text-ink">Scan a document</h2>
        <p className="mb-4 mt-2 text-[15px] text-ink-muted">The request shape:</p>
        <pre className={codeClass}>
          <code>{`POST /api/v1/scan
Content-Type: multipart/form-data

field: file=<your document>`}</code>
        </pre>

        <p className="mb-3 mt-6 text-[15px] text-ink-muted">Example:</p>
        <pre className={codeClass}>
          <code>{`curl -F "file=@invoice.pdf" ${API_BASE}/api/v1/scan`}</code>
        </pre>

        <p className="mb-3 mt-6 text-[15px] text-ink-muted">
          Response <code className="text-ink">200 OK</code>:
        </p>
        <pre className={codeClass}>
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
      </Section>

      <Section>
        <h2 className="m-0 text-xl font-bold text-ink">Errors</h2>
        <p className="mb-5 mt-2 text-[15px] text-ink-muted">
          Failures are explicit — nothing fails silently.
        </p>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {errors.map(({ code, body }) => (
            <Card key={code} padding="sm">
              {/* Status codes were <h3> before, which collided with real section
                  headings at the same level. They are labels, not headings. */}
              <Badge tone={code.startsWith("4") ? "warning" : "danger"} uppercase>
                {code}
              </Badge>
              <p className="mt-2.5 text-[15px] leading-relaxed text-ink-muted">{body}</p>
            </Card>
          ))}
        </div>

        <h2 className="mb-3 mt-10 text-xl font-bold text-ink">Health</h2>
        <pre className={codeClass}>
          <code>{`GET /api/v1/health  ->  { "status": "UP", "engine": "UP" }`}</code>
        </pre>

        <div className="mt-8">
          <ButtonLink href={`${API_BASE}/docs`} variant="secondary" target="_blank" rel="noreferrer">
            Full interactive reference <ExternalLink size={16} aria-hidden />
          </ButtonLink>
        </div>
      </Section>
    </>
  );
}
