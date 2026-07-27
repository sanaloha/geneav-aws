import {
  BarChart3,
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
const LAST_UPDATED = "July 26, 2026";

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
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            If you arrive from a link or a campaign URL, we store{" "}
            <strong>where that visit came from</strong> — the referring website and any{" "}
            <code>utm_*</code> parameters in the address — against your account when you sign up.
            It tells us which articles and links are worth writing more of. It is recorded once, at
            signup, and is never used to build a profile of you or shared with anyone.
          </p>
        </Card>

        <Card>
          <FileScan size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">The documents you scan</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Files you submit for scanning are <strong>streamed straight to the detection engine and
            are not written to disk or retained</strong> once the scan completes. We do not keep a
            copy of your file contents. For operational and diagnostic purposes our server logs
            record only the <strong>file extension and size</strong> alongside the verdict —{" "}
            <strong>not the file, and not its name</strong>, since filenames themselves often
            contain personal information.
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
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            Your browser also keeps the referral information described above in{" "}
            <strong>sessionStorage</strong> until you sign up or close the tab. It is not a cookie,
            it is never sent to anyone but us, and it is discarded with the tab.
          </p>
        </Card>

        <Card>
          <BarChart3 size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">Website analytics</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            We count page views using <strong>Umami</strong>, which we{" "}
            <strong>run ourselves on our own server</strong> — no analytics provider receives your
            data, because there is no analytics provider. It is{" "}
            <strong>cookieless and does not track you across websites</strong>: it records the page,
            the referring site, and coarse details like country, browser and device type, with no
            identifier that persists between visits. We also count a handful of actions in the same
            way — that a scan was run, that an account was created — never who did them.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            This is why you have not been shown a cookie consent banner. There is nothing to consent
            to, and we would rather keep it that way than gain a little more detail.
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
              <strong>Hostinger</strong> — sends transactional email such as signup
              acknowledgements and password-reset links. Your email address is shared with
              Hostinger for this purpose.
            </li>
            <li>
              <strong>OpenAI</strong> — powers the optional chat assistant. Messages you send to the
              assistant are forwarded to OpenAI to generate a reply. Do not paste sensitive data
              into the chat.
            </li>
            <li>
              <strong>Microsoft</strong> — paid plans are sold through the Azure Marketplace, where
              Microsoft is the merchant of record and processes your billing details; we receive
              subscription details (plan, status, purchaser email) but never your payment method.
              If you sign in with Microsoft, we receive your name, email address, and directory
              identifiers from Microsoft Entra ID.
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
            data; because the database is backed up nightly and those backups are kept for{" "}
            <strong>14 days</strong>, a copy of your record can persist in a backup for up to that
            long before it ages out.
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
