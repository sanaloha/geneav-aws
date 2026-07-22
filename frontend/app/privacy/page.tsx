import {
  Cookie,
  Database,
  FileScan,
  Mail,
  Share2,
  ShieldCheck,
  Timer,
  UserCog,
} from "lucide-react";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "Privacy Policy",
  description:
    "How geneav collects, uses, and protects your data — account details, API keys, usage, and the documents you scan.",
  alternates: { canonical: "/privacy" },
};

const linkClass = "font-semibold text-brand";

// Shown as the "last updated" date. Kept explicit rather than computed so it
// reflects when the policy text actually changed, not when the page rendered.
const LAST_UPDATED = "July 22, 2026";

export default function Privacy() {
  return (
    <Section>
      <PageHeader
        title="Privacy Policy"
        lead="This policy explains what data geneav collects when you use the website and API, why we collect it, and the choices you have. We keep it short and specific."
      />
      <p className="-mt-3 mb-8 text-sm text-ink-muted">Last updated: {LAST_UPDATED}</p>

      <div className="space-y-5">
        <Card>
          <Database size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Information we collect</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            When you create an account we store your <strong>email address</strong>, a{" "}
            <strong>BCrypt hash of your password</strong> (never the password itself), and your{" "}
            plan. When you create an API key we store only a <strong>one-way hash</strong> of it
            plus a short prefix and the last four characters so you can recognise it — the full key
            is shown once, at creation, and cannot be recovered afterwards. We also record{" "}
            <strong>usage metrics</strong> (such as your monthly scan count) to enforce plan quotas.
          </p>
        </Card>

        <Card>
          <FileScan size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">The documents you scan</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Files you submit for scanning are <strong>streamed straight to the detection engine and
            are not written to disk or retained</strong> once the scan completes. We do not keep a
            copy of your file contents. For operational and diagnostic purposes our server logs
            record the request&apos;s <strong>file name, size, and declared content type</strong>{" "}
            alongside the verdict — but not the file itself.
          </p>
        </Card>

        <Card>
          <Cookie size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Cookies &amp; sessions</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            When you sign in we set a single <strong>HttpOnly, SameSite=Lax session cookie</strong>{" "}
            so the browser can prove who you are on later requests. It is not readable by JavaScript
            and is used only to keep you signed in. We do not use advertising or third-party
            tracking cookies.
          </p>
        </Card>

        <Card>
          <Share2 size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Third-party services</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav relies on a small number of providers to run the service:
          </p>
          <ul className="mt-3 list-disc space-y-2 pl-5 text-[15px] leading-relaxed text-ink-muted">
            <li>
              <strong>ClamAV</strong> — the open-source engine that performs the malware detection.
              See our{" "}
              <a href="/legal" className={linkClass}>
                legal &amp; attribution
              </a>{" "}
              page for details.
            </li>
            <li>
              <strong>Resend</strong> — sends transactional email such as signup acknowledgements
              and password-reset links. Your email address is shared with Resend for this purpose.
            </li>
            <li>
              <strong>OpenAI</strong> — powers the optional chat assistant. Messages you send to the
              assistant are forwarded to OpenAI to generate a reply. Do not paste sensitive data
              into the chat.
            </li>
          </ul>
        </Card>

        <Card>
          <Timer size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Data retention</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            We keep your account data, API keys, and usage records for as long as your account is
            active. Password-reset links are short-lived and expire automatically. Scanned file
            contents are not retained at all. When you close your account we delete your account
            data; some records may persist briefly in backups before they age out.
          </p>
        </Card>

        <Card>
          <UserCog size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Your rights &amp; choices</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            You can revoke any API key from your dashboard at any time, and you can request access
            to, correction of, or deletion of your account data by emailing us. We will respond to
            legitimate requests within a reasonable time.
          </p>
        </Card>

        <Card>
          <ShieldCheck size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Changes to this policy</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            We may update this policy as the service evolves. When we do, we&apos;ll revise the
            &ldquo;last updated&rdquo; date above. Material changes will be communicated through the
            website.
          </p>
        </Card>

        <Card>
          <Mail size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Contact</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Questions about your privacy or this policy? Email{" "}
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
