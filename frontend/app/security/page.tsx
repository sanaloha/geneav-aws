import {
  Bug,
  FileScan,
  Gauge,
  KeyRound,
  ListChecks,
  Lock,
  Mail,
  Server,
  Share2,
  ShieldAlert,
} from "lucide-react";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "Security",
  description:
    "How geneav secures your data — file handling, encryption, credential storage, infrastructure, subprocessors, and vulnerability disclosure.",
  alternates: { canonical: "/security" },
};

const linkClass = "font-semibold text-brand";

// Explicit rather than computed, so it reflects when the content actually
// changed. Matches the privacy policy and terms.
const LAST_UPDATED = "July 25, 2026";

// Every third party that touches customer data, for security questionnaires.
const subprocessors = [
  {
    name: "Microsoft Azure",
    purpose: "Hosting and compute — all application and database infrastructure",
    data: "All service data at rest and in transit",
    location: "East US",
  },
  {
    name: "Microsoft (Marketplace & Entra ID)",
    purpose:
      "Paid-plan billing (merchant of record for Azure Marketplace purchases) and optional Microsoft sign-in",
    data: "Purchase and subscription details; sign-in identity (name, email, tenant)",
    location: "Global",
  },
  {
    name: "Hostinger",
    purpose: "Transactional email — signup acknowledgements and password resets",
    data: "Email address",
    location: "EU",
  },
  {
    name: "OpenAI",
    purpose: "Optional chat assistant on the website",
    data: "Chat messages you type into the widget",
    location: "US",
  },
];

export default function Security() {
  return (
    <Section>
      <PageHeader
        title="Security"
        lead="How geneav handles your files, stores your credentials, and runs its infrastructure — plus an honest account of what we have not built yet."
      />
      <p className="-mt-3 mb-8 text-sm text-ink-muted">Last updated: {LAST_UPDATED}</p>

      <div className="space-y-5">
        <Card>
          <FileScan size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">How your files are handled</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            A file you submit is <strong>streamed directly to the detection engine</strong> and is
            not stored. The API reads the upload from the request and writes it to the scanning
            daemon over a local socket — no copy is persisted to a database, object store, or backup.
            Once the verdict is returned, the file is gone.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            Our server logs record only the <strong>file extension, size, and verdict</strong> for
            operational and diagnostic purposes — never the file contents, and never the filename,
            which frequently carries personal information. Scanning happens entirely within our own
            infrastructure — your files are not sent to any third party, and they are not used to
            train anything.
          </p>
        </Card>

        <Card>
          <Lock size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Encryption in transit</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            All traffic to geneav is served over HTTPS, with certificates issued and renewed
            automatically by Let&apos;s Encrypt. Plain HTTP requests are redirected to HTTPS. Inside
            our infrastructure, services communicate over a private Docker network that is not
            reachable from the internet.
          </p>
        </Card>

        <Card>
          <KeyRound size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Credentials and authentication</h2>
          <ul className="mt-2 list-disc space-y-2 pl-5 text-[15px] leading-relaxed text-ink-muted">
            <li>
              <strong>Passwords</strong> are hashed with BCrypt. We never store or log the plaintext.
              Our policy requires at least 12 characters drawn from three of four character classes,
              and rejects common passwords and passwords containing your email address.
            </li>
            <li>
              <strong>API keys</strong> are generated from a cryptographically secure random source
              and stored only as a one-way SHA-256 hash. We keep a short prefix and the last four
              characters so you can recognise a key in your dashboard. The full key is shown once, at
              creation, and cannot be recovered — only revoked and replaced.
            </li>
            <li>
              <strong>Sessions</strong> use an HttpOnly, SameSite=Lax cookie that JavaScript cannot
              read. The session identifier is rotated on sign-in to prevent session fixation, and
              changing your password invalidates every existing session.
            </li>
            <li>
              <strong>Password resets</strong> use a single-use token that is itself stored hashed,
              expires after 10 minutes, and is rate limited. The reset endpoint returns the same
              response whether or not an account exists, so it cannot be used to enumerate users.
            </li>
          </ul>
        </Card>

        <Card>
          <Gauge size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Abuse and availability protection</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav applies rate limiting at two independent layers: a per-IP limit at the reverse
            proxy, and per-account token buckets in the application, with a tighter budget on
            authentication endpoints to resist password guessing. Rate limiting runs{" "}
            <strong>before the request body is read</strong>, so a throttled upload is rejected
            without consuming server resources.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            A global cap on simultaneous scans protects the detection engine from resource
            exhaustion, and uploads are capped at 25 MB. Together these mean a single noisy client
            cannot degrade the service for everyone else.
          </p>
        </Card>

        <Card>
          <Server size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Infrastructure</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav runs on Microsoft Azure in the East US region. Application components run as
            isolated containers; only ports 80 and 443 are reachable from the internet, enforced at
            the network security group. Administrative access is restricted to a small set of known
            addresses.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            Deployments are automated from version control and require no inbound SSH access —
            releases are delivered through Azure&apos;s authenticated management plane using
            short-lived federated credentials, with automatic rollback if a build fails. Malware
            signature databases are updated automatically and regularly from the ClamAV project.
          </p>
        </Card>

        <Card>
          <Share2 size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Subprocessors</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            These are the only third parties that process data on our behalf. Note that{" "}
            <strong>none of them receive the files you scan</strong> — scanning is performed
            in-house.
          </p>
          <div className="mt-4 overflow-x-auto">
            <table className="w-full border-collapse text-left text-[14px]">
              <thead>
                <tr className="border-b border-line">
                  <th className="py-2 pr-4 font-bold text-ink">Provider</th>
                  <th className="py-2 pr-4 font-bold text-ink">Purpose</th>
                  <th className="py-2 pr-4 font-bold text-ink">Data</th>
                  <th className="py-2 font-bold text-ink">Region</th>
                </tr>
              </thead>
              <tbody>
                {subprocessors.map((s) => (
                  <tr key={s.name} className="border-b border-line last:border-0">
                    <td className="py-3 pr-4 font-semibold text-ink">{s.name}</td>
                    <td className="py-3 pr-4 text-ink-muted">{s.purpose}</td>
                    <td className="py-3 pr-4 text-ink-muted">{s.data}</td>
                    <td className="py-3 text-ink-muted">{s.location}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-4 text-[15px] leading-relaxed text-ink-muted">
            The chat assistant is optional and separate from the scanning API. Please do not paste
            sensitive data into it. See our{" "}
            <a href="/privacy" className={linkClass}>
              Privacy Policy
            </a>{" "}
            for how each category of data is used.
          </p>
        </Card>

        <Card>
          <Bug size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Reporting a vulnerability</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            If you believe you have found a security issue, please email{" "}
            <a href="mailto:admin@geneav.com" className={linkClass}>
              admin@geneav.com
            </a>{" "}
            with enough detail to reproduce it. We aim to acknowledge reports within{" "}
            <strong>3 business days</strong> and to keep you updated until the issue is resolved.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            We ask that you give us a reasonable opportunity to fix an issue before disclosing it
            publicly, avoid accessing or modifying other users&apos; data, and refrain from testing
            that degrades the service for others — no denial-of-service or high-volume automated
            scanning. We will not pursue action against researchers who follow these guidelines in
            good faith. We do not currently run a paid bug bounty, but we are glad to credit you.
          </p>
        </Card>

        <Card>
          <ListChecks size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">What we have not built yet</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Most security pages list only strengths. We would rather you learn these from us than
            discover them later:
          </p>
          <ul className="mt-3 list-disc space-y-2 pl-5 text-[15px] leading-relaxed text-ink-muted">
            <li>
              <strong>No SOC 2, ISO 27001, or third-party penetration test.</strong> These are on the
              roadmap and honestly represented as absent today.
            </li>
            <li>
              <strong>Single region, no failover.</strong> All processing happens in Azure East US.
              We cannot currently offer EU or other data-residency options, and an outage in that
              region is an outage for the service.
            </li>
            <li>
              <strong>No scan history or audit log.</strong> Verdicts are returned to you and not
              retained, so we cannot reconstruct a record of past scans on your behalf.
            </li>
            <li>
              <strong>No SSO, teams, or role-based access.</strong> Accounts are individual, with a
              single permission level.
            </li>
            <li>
              <strong>No uptime SLA.</strong> We do not currently offer a contractual availability
              commitment.
            </li>
          </ul>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            If one of these is a blocker for you, email us — knowing which gaps matter most is how we
            decide what to build next.
          </p>
        </Card>

        <Card>
          <ShieldAlert size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">A note on detection</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav&apos;s detection is signature-based, powered by ClamAV. It reliably catches known
            malware, but a clean verdict is not proof that a file is safe — novel or deliberately
            obfuscated threats can pass. geneav is one layer in a security programme, not a
            replacement for endpoint protection, access control, and patching. Our{" "}
            <a href="/terms" className={linkClass}>
              Terms of Service
            </a>{" "}
            set this out in full.
          </p>
        </Card>

        <Card>
          <Mail size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Security contact</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            For vulnerability reports, security questionnaires, or data-processing agreements, email{" "}
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
