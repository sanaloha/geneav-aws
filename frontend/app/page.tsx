import {
  Activity,
  ArrowRight,
  BookOpen,
  CheckCircle2,
  FileSearch,
  Gauge,
  Lock,
  Plug,
  ScanLine,
  ShieldCheck,
  Upload,
  Zap,
} from "lucide-react";
import Badge from "./ui/Badge";
import { ButtonLink } from "./ui/Button";
import Card from "./ui/Card";
import PageHeader from "./ui/PageHeader";
import Section from "./ui/Section";

/**
 * Every claim on this page must be true of the current build — no invented
 * customers, testimonials, user counts, or uptime figures.
 */

const trustSignals = [
  { icon: ShieldCheck, label: "ClamAV engine" },
  { icon: Lock, label: "Files never stored" },
  { icon: BookOpen, label: "OpenAPI documented" },
  { icon: Activity, label: "Health endpoint" },
  { icon: Zap, label: "Streaming scans" },
  { icon: FileSearch, label: "Structured verdicts" },
];

const steps = [
  {
    icon: Upload,
    title: "Send the file",
    body: "POST a multipart request to a single endpoint. No SDK, no agent, no queue to run.",
  },
  {
    icon: ScanLine,
    title: "We stream it to the engine",
    body: "The file is piped straight to ClamAV over its native protocol. It is never written to disk.",
  },
  {
    icon: CheckCircle2,
    title: "Get a structured verdict",
    body: "JSON back with clean or infected, the threat name when there is one, and a scan id.",
  },
];

const features = [
  {
    icon: ShieldCheck,
    title: "Real detection",
    body: "Backed by the ClamAV engine and its constantly updated signature database — the same technology trusted across the industry.",
  },
  {
    icon: Zap,
    title: "One endpoint",
    body: "POST /api/v1/scan with a file. Get back a JSON verdict with the threat name, file metadata, and a scan id.",
  },
  {
    icon: Plug,
    title: "Drop-in ready",
    body: "Language-agnostic HTTP API and OpenAPI docs. Wire it into uploads, inboxes, or pipelines in minutes.",
  },
  {
    icon: Lock,
    title: "Nothing persisted",
    body: "Documents are streamed through the scanner and discarded. We do not keep your files after the verdict.",
  },
  {
    icon: Gauge,
    title: "Quotas and rate limits",
    body: "Per-key monthly quotas and per-minute rate limits, with explicit 402 and 429 responses rather than silent failure.",
  },
  {
    icon: BookOpen,
    title: "Interactive docs",
    body: "Swagger UI at /docs and a machine-readable OpenAPI document, so you can try calls before writing code.",
  },
];

// Quotas and rates here must match `geneav.plans.definitions` in the backend's
// application.yml — that config is what actually meters and throttles.
// There is no checkout yet, so every paid CTA opens an email rather than
// implying self-serve upgrade. Swap to billing links once Stripe is wired up.

// The free quota appears twice on this page: in the pricing card and under the
// hero CTA. It is defined once here because those two drifted apart when the
// tier was raised from 100 to 500 — the hero kept advertising the old number.
const FREE_SCAN_QUOTA = 500;

const plans = [
  {
    name: "Free",
    price: "$0",
    period: "/month",
    note: "No card required",
    cta: { label: "Create an API key", href: "/login", variant: "secondary" as const },
    limits: [
      `${FREE_SCAN_QUOTA.toLocaleString("en-US")} scans per month`,
      "10 requests per minute",
      "Full REST API access",
      "OpenAPI docs",
    ],
  },
  {
    name: "Starter",
    price: "$19",
    period: "/month",
    note: "Billed monthly",
    cta: { label: "Get in touch", href: "mailto:admin@geneav.com", variant: "secondary" as const },
    limits: ["10,000 scans per month", "30 requests per minute", "Everything in Free"],
  },
  {
    name: "Pro",
    price: "$39",
    period: "/month",
    note: "Billed monthly",
    cta: { label: "Get in touch", href: "mailto:admin@geneav.com", variant: "primary" as const },
    limits: [
      "100,000 scans per month",
      "120 requests per minute",
      "Everything in Starter",
      "Priority support",
    ],
    featured: true,
  },
  {
    name: "Scale",
    price: "$149",
    period: "/month",
    note: "Billed monthly",
    cta: { label: "Get in touch", href: "mailto:admin@geneav.com", variant: "secondary" as const },
    limits: [
      "500,000 scans per month",
      "300 requests per minute",
      "Everything in Pro",
      "Volume pricing available",
    ],
  },
];

// These answers are also stated in the chat assistant's system prompt
// (backend ChatService.java) and as canned chip answers under
// geneav.chat.suggestions in application.yml. Change one, check the other two.
const faqs = [
  {
    q: "What does geneav actually detect?",
    a: "Known malware, viruses, trojans, worms, and malicious documents, using ClamAV's signature database. Like any signature-based scanner it does not catch everything — it complements layered security rather than replacing it.",
  },
  {
    q: "What file types and sizes are supported?",
    a: "PDF, Microsoft Office documents, plain text, CSV, RTF, and ZIP archives, up to 25 MB per upload. Unsupported types return 415 and oversized uploads return 413.",
  },
  {
    q: "Do you store the documents I send?",
    a: "No. Files are streamed to the scan engine and discarded once the verdict is produced. They are not written to disk or retained after the response.",
  },
  {
    q: "What happens when I hit my quota?",
    a: "You get an explicit 402 when the monthly scan quota is exhausted, and a 429 with a Retry-After header when you exceed the per-minute rate limit. Nothing fails silently.",
  },
  {
    q: "How is ClamAV licensed here?",
    a: "geneav talks to ClamAV over a network socket and does not link libclamav. ClamAV is GNU GPL v2 and its attribution is on our legal page.",
  },
];

export default function Home() {
  return (
    <>
      {/* Hero */}
      <Section size="hero">
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <div>
            <Badge tone="brand">
              <ShieldCheck size={14} aria-hidden /> Powered by ClamAV
            </Badge>
            <h1 className="mt-5 text-4xl font-extrabold leading-[1.1] tracking-tight text-ink sm:text-5xl">
              Scan every document for malware.
            </h1>
            <p className="mt-5 max-w-xl text-lg leading-relaxed text-ink-muted">
              geneav is an antivirus built for documents. Drop in a file and get an instant
              clean-or-infected verdict — from our website or straight from your code via a
              simple REST API.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <ButtonLink href="/developers" variant="primary" size="lg">
                Try a scan <ArrowRight size={18} aria-hidden />
              </ButtonLink>
              <ButtonLink href="/features" variant="secondary" size="lg">
                How it works
              </ButtonLink>
            </div>
            <p className="mt-4 text-[13px] text-ink-subtle">
              Free tier — {FREE_SCAN_QUOTA.toLocaleString("en-US")} scans a month, no card
              required.
            </p>
          </div>

          {/* Product visual: the actual verdict shape, not a stock illustration. */}
          <div className="relative" aria-hidden>
            <div className="rounded-card border border-line bg-surface p-5 shadow-lg">
              <div className="flex items-center gap-2 border-b border-line pb-3">
                <span className="h-3 w-3 rounded-full bg-danger/70" />
                <span className="h-3 w-3 rounded-full bg-warning/60" />
                <span className="h-3 w-3 rounded-full bg-success-accent/70" />
                <span className="ml-2 font-mono text-xs text-ink-subtle">POST /api/v1/scan</span>
              </div>

              <div className="mt-4 flex items-center gap-3 rounded-[10px] border border-success-border bg-success-bg p-3">
                <CheckCircle2 size={22} className="shrink-0 text-success" />
                <div>
                  <div className="text-sm font-bold text-success">CLEAN</div>
                  <div className="font-mono text-xs text-ink-muted">invoice.pdf · 48 KB</div>
                </div>
              </div>

              <div className="mt-3 flex items-center gap-3 rounded-[10px] border border-danger-border bg-danger-bg p-3">
                <ShieldCheck size={22} className="shrink-0 text-danger" />
                <div>
                  <div className="text-sm font-bold text-danger">INFECTED</div>
                  <div className="font-mono text-xs text-ink-muted">Eicar-Test-Signature</div>
                </div>
              </div>

              <pre className="mt-4 max-h-40 overflow-hidden rounded-[10px] border border-line bg-surface-sunken p-3 text-[11.5px] leading-relaxed">
                <code>{`{
  "scanId": "a1b2c3d4-…",
  "status": "clean",
  "threat": null,
  "fileSize": 48213
}`}</code>
              </pre>
            </div>
          </div>
        </div>
      </Section>

      {/* Trust band — verifiable facts only */}
      <Section tone="subtle" size="tight">
        <ul className="m-0 flex list-none flex-wrap items-center justify-center gap-x-8 gap-y-3 p-0">
          {trustSignals.map(({ icon: Icon, label }) => (
            <li key={label} className="flex items-center gap-2 text-sm font-semibold text-ink-muted">
              <Icon size={16} className="text-brand" aria-hidden />
              {label}
            </li>
          ))}
        </ul>
      </Section>

      {/* How it works */}
      <Section>
        <PageHeader
          level="h2"
          align="center"
          title="From upload to verdict in one call"
          lead="Three steps, no infrastructure to run on your side."
        />
        <div className="grid gap-5 md:grid-cols-3">
          {steps.map(({ icon: Icon, title, body }, i) => (
            <Card key={title}>
              <div className="mb-4 flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-[10px] bg-brand-bg text-brand">
                  <Icon size={20} aria-hidden />
                </span>
                <span className="text-xs font-extrabold uppercase tracking-wider text-ink-subtle">
                  Step {i + 1}
                </span>
              </div>
              <h3 className="m-0 text-lg font-bold text-ink">{title}</h3>
              <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">{body}</p>
            </Card>
          ))}
        </div>
      </Section>

      {/* Features */}
      <Section tone="subtle">
        <PageHeader
          level="h2"
          align="center"
          title="Everything you need to scan uploads"
          lead="Built to sit in front of user-generated files in any application."
        />
        <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {features.map(({ icon: Icon, title, body }) => (
            <Card key={title} interactive>
              <Icon size={22} className="mb-3 text-brand" aria-hidden />
              <h3 className="m-0 text-lg font-bold text-ink">{title}</h3>
              <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">{body}</p>
            </Card>
          ))}
        </div>
      </Section>

      {/* Code */}
      <Section>
        <div className="grid items-center gap-10 lg:grid-cols-2">
          <div>
            <PageHeader
              level="h2"
              title="One request. One structured answer."
              lead="Send a document, get JSON back. No SDK required — it is just HTTP."
            />
            <ButtonLink href="/developers" variant="secondary">
              Read the API docs <ArrowRight size={16} aria-hidden />
            </ButtonLink>
          </div>
          <pre className="m-0 overflow-x-auto rounded-card border border-line bg-surface-sunken p-5 text-[13px] leading-relaxed">
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
      </Section>

      {/* Pricing */}
      <Section tone="subtle" id="pricing">
        <PageHeader
          level="h2"
          align="center"
          title="Start free, scale when you need to"
          lead="Quotas are enforced per API key, with explicit responses when you reach them."
        />
        {/* Four tiers, so this uses the full content width rather than the
            max-w-3xl the two-tier layout needed. */}
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {plans.map((plan) => (
            <Card
              key={plan.name}
              className={plan.featured ? "border-brand ring-1 ring-brand" : undefined}
            >
              <div className="flex items-center justify-between gap-2">
                <h3 className="m-0 text-lg font-bold text-ink">{plan.name}</h3>
                {/* "Recommended" rather than "Most popular" — there is no usage
                    data to support a popularity claim, and Scale now has the
                    most capacity, so the old label would be wrong too. */}
                {plan.featured && <Badge tone="brand">Recommended</Badge>}
              </div>
              <div className="mt-3 flex items-baseline gap-1">
                <span className="text-3xl font-extrabold tracking-tight text-ink">{plan.price}</span>
                <span className="text-[15px] font-semibold text-ink-subtle">{plan.period}</span>
              </div>
              <div className="mt-1 text-[13px] text-ink-subtle">{plan.note}</div>
              <ul className="my-6 list-none space-y-2.5 p-0">
                {plan.limits.map((limit) => (
                  <li key={limit} className="flex items-start gap-2.5 text-[15px] text-ink-muted">
                    <CheckCircle2 size={17} className="mt-0.5 shrink-0 text-success-accent" aria-hidden />
                    {limit}
                  </li>
                ))}
              </ul>
              <ButtonLink href={plan.cta.href} variant={plan.cta.variant} fullWidth>
                {plan.cta.label}
              </ButtonLink>
            </Card>
          ))}
        </div>
      </Section>

      {/* FAQ */}
      <Section tone="subtle">
        <PageHeader level="h2" align="center" title="Questions, answered honestly" />
        <div className="mx-auto max-w-3xl space-y-3">
          {faqs.map(({ q, a }) => (
            <details
              key={q}
              className="group rounded-card border border-line bg-surface px-5 py-4 shadow-sm"
            >
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-bold text-ink">
                {q}
                <ArrowRight
                  size={18}
                  aria-hidden
                  className="shrink-0 text-ink-subtle transition-transform group-open:rotate-90"
                />
              </summary>
              <p className="mb-0 mt-3 text-[15px] leading-relaxed text-ink-muted">{a}</p>
            </details>
          ))}
        </div>
      </Section>

      {/* Closing CTA. pb is oversized on purpose: the chat bubble is fixed at
          bottom-right and would otherwise sit on top of the button. */}
      <Section tone="dark" className="text-center">
        <h2 className="m-0 text-3xl font-extrabold tracking-tight sm:text-4xl">
          Start scanning in the next five minutes.
        </h2>
        <p className="mx-auto mt-4 max-w-xl text-[17px] leading-relaxed text-white/70">
          Create a free API key and send your first document. No card, no sales call.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3 pb-16 sm:pb-8">
          <ButtonLink href="/login" variant="primary" size="lg">
            Create an API key <ArrowRight size={18} aria-hidden />
          </ButtonLink>
          <ButtonLink
            href="/developers"
            size="lg"
            variant="secondary"
            className="!border-white/25 !bg-transparent !text-white hover:!border-white hover:!text-white"
          >
            See the docs
          </ButtonLink>
        </div>
      </Section>
    </>
  );
}
