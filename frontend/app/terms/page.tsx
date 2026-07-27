import {
  Activity,
  AlertTriangle,
  Ban,
  Copyright,
  CreditCard,
  FileScan,
  FileText,
  Gavel,
  KeyRound,
  Mail,
  RefreshCw,
  Scale,
  Server,
  ShieldOff,
  UserCheck,
  UserX,
} from "lucide-react";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "Terms of Service",
  description:
    "The terms governing your use of the geneav website and malware scanning API — accounts, acceptable use, plans, detection limits, and liability.",
  alternates: { canonical: "/terms" },
};

const linkClass = "font-semibold text-brand";

// Explicit rather than computed, so it reflects when the terms actually
// changed — not when the page rendered. Matches the privacy policy.
const LAST_UPDATED = "July 25, 2026";

export default function Terms() {
  return (
    <Section>
      <PageHeader
        title="Terms of Service"
        lead="These terms govern your use of the geneav website and API. They explain what we provide, what we ask of you, and — importantly — the limits of what a malware scanner can promise."
      />
      <p className="-mt-3 mb-8 text-sm text-ink-muted">Last updated: {LAST_UPDATED}</p>

      <div className="space-y-5">
        <Card>
          <FileText size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">1. Agreement to these terms</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav (&ldquo;geneav&rdquo;, &ldquo;we&rdquo;, &ldquo;us&rdquo;) is operated by Santosh
            Singh. By creating an account, calling the API, or otherwise using the service, you agree
            to these terms. If you are agreeing on behalf of an organisation, you confirm you have
            authority to bind that organisation. If you do not agree, do not use the service. These
            terms work alongside our{" "}
            <a href="/privacy" className={linkClass}>
              Privacy Policy
            </a>
            .
          </p>
        </Card>

        <Card>
          <Server size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">2. What geneav provides</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav is a hosted REST API that accepts a document you upload, scans it for known
            malware using the ClamAV detection engine, and returns a verdict. It is a{" "}
            <strong>detection service, not a remediation service</strong> — we return a result, and
            what you do with it is your decision. geneav does not quarantine, clean, repair, or
            delete files, and it is not endpoint antivirus software.
          </p>
        </Card>

        <Card>
          <UserCheck size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">3. Accounts and eligibility</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            You must be at least 18 years old and legally able to enter a contract. You agree to
            provide an accurate email address, keep your password confidential, and tell us promptly
            at{" "}
            <a href="mailto:admin@geneav.com" className={linkClass}>
              admin@geneav.com
            </a>{" "}
            if you believe your account has been compromised. You are responsible for all activity
            under your account.
          </p>
        </Card>

        <Card>
          <KeyRound size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">4. API keys</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            API keys authenticate requests as you. Treat them as credentials: do not commit them to
            source control, embed them in client-side code, or share them publicly. We store only a
            one-way hash of each key and cannot recover the original — if a key is lost or exposed,
            revoke it from your dashboard and create a new one. You are responsible for usage and
            charges incurred through your keys, including by anyone you give them to.
          </p>
        </Card>

        <Card>
          <Ban size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">5. Acceptable use</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">You agree not to:</p>
          <ul className="mt-3 list-disc space-y-2 pl-5 text-[15px] leading-relaxed text-ink-muted">
            <li>
              Use geneav to develop, refine, or validate malware designed to evade detection — for
              example, iteratively testing samples against the API until they return clean.
            </li>
            <li>
              Circumvent or attempt to circumvent quotas, rate limits, or authentication, including
              by creating multiple accounts to exceed free-tier limits.
            </li>
            <li>
              Resell, sublicense, or expose the API as a competing scanning service without a written
              agreement with us.
            </li>
            <li>
              Submit content you have no lawful right to submit, or content whose transmission
              violates applicable law, export controls, or third-party rights.
            </li>
            <li>
              Attempt to disrupt or degrade the service, probe it for vulnerabilities outside our
              disclosure process, or gain unauthorised access to any part of the infrastructure.
            </li>
            <li>
              Use the service in any application where a missed detection could reasonably lead to
              death, personal injury, or severe environmental damage.
            </li>
          </ul>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            Security researchers acting in good faith should see our{" "}
            <a href="/security" className={linkClass}>
              security page
            </a>{" "}
            for how to report a vulnerability.
          </p>
        </Card>

        <Card>
          <CreditCard size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">6. Plans, quotas, and payment</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            Each plan includes a monthly scan quota and a request rate limit, both described on our
            pricing page and enforced by the API. Quotas reset at the start of each calendar month
            (UTC). Requests beyond your quota are declined rather than billed as overage. Successful
            scans count against your quota; failed requests do not.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            Paid plans are purchased through the <strong>Microsoft Azure Marketplace</strong>.
            Microsoft is the merchant of record: it collects payment, applies any taxes, and bills
            your Azure account monthly in advance under the Microsoft Customer Agreement.
            Cancellation and any refunds run through Microsoft&apos;s marketplace terms — cancel
            from the Azure portal at any time and you retain access until the end of the paid
            period. Where a plan offers a free trial, no charge is made if you cancel before the
            trial ends.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            We may change pricing with at least 30 days&apos; notice; changes take effect at your
            next renewal. Free-tier access is provided directly by us at our discretion and may be
            modified or withdrawn.
          </p>
        </Card>

        <Card tone="warning">
          <AlertTriangle size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">
            7. Detection is not guaranteed — please read this
          </h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            geneav uses <strong>signature-based detection</strong>. It identifies malware that
            matches a known signature in the ClamAV database. This means:
          </p>
          <ul className="mt-3 list-disc space-y-2 pl-5 text-[15px] leading-relaxed text-ink-muted">
            <li>
              <strong>A &ldquo;clean&rdquo; verdict is not a guarantee that a file is safe.</strong>{" "}
              New, targeted, obfuscated, or zero-day malware may not be detected.
            </li>
            <li>
              <strong>False positives are possible.</strong> A file may be flagged as infected when it
              is not.
            </li>
            <li>
              geneav performs no behavioural analysis, sandboxing, or content disarm and
              reconstruction.
            </li>
          </ul>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            geneav is one control in a layered security programme — it is{" "}
            <strong>not a substitute</strong> for endpoint protection, access controls, patching,
            backups, or user training. You remain solely responsible for your own security posture
            and for any decision you make based on a scan result. We make no representation that the
            service will detect any particular threat.
          </p>
        </Card>

        <Card>
          <FileScan size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">8. The files you submit</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            You keep all rights to the files you submit. You grant us only the limited, temporary
            licence needed to transmit a file to the detection engine and return a verdict. As
            described in our{" "}
            <a href="/privacy" className={linkClass}>
              Privacy Policy
            </a>
            , file contents are streamed to the engine and are not retained after the scan completes.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            You confirm you have the right to submit each file for scanning, and that doing so does
            not breach any confidentiality obligation, data-protection law, or third-party right that
            applies to you. If you are subject to data-residency requirements, note that scanning is
            currently performed in a single region — see our{" "}
            <a href="/security" className={linkClass}>
              security page
            </a>
            .
          </p>
        </Card>

        <Card>
          <Activity size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">9. Availability and changes</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            We aim to keep geneav available and reliable, but we do{" "}
            <strong>not offer a service level agreement</strong> unless one is separately agreed in
            writing. The service may be unavailable for maintenance, updates, or reasons beyond our
            control. We may add, change, or remove features, and we will give reasonable notice of
            changes that materially reduce functionality you rely on.
          </p>
        </Card>

        <Card>
          <UserX size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">10. Suspension and termination</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            You may close your account at any time. We may suspend or terminate access if you breach
            these terms, if your usage threatens the stability or security of the service, if payment
            fails, or if we are required to by law. Where circumstances allow, we will give notice
            and an opportunity to resolve the issue first. On termination your right to use the
            service ends immediately; sections that by their nature should survive — including
            sections 7, 12, 13, and 14 — do survive.
          </p>
        </Card>

        <Card>
          <Copyright size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">11. Intellectual property</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            The geneav website, API, and application code are proprietary and remain our property.
            These terms grant you a limited, revocable, non-exclusive, non-transferable right to use
            the service as documented — and nothing more. geneav&apos;s detection engine is ClamAV,
            which is open-source software licensed under the GNU GPL v2; see our{" "}
            <a href="/legal" className={linkClass}>
              legal &amp; attribution
            </a>{" "}
            page. If you send us feedback or suggestions, we may use them without obligation to you.
          </p>
        </Card>

        <Card>
          <ShieldOff size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">12. Disclaimer of warranties</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            To the fullest extent permitted by law, the service is provided{" "}
            <strong>&ldquo;as is&rdquo; and &ldquo;as available&rdquo;</strong>, without warranties
            of any kind, whether express, implied, or statutory — including any implied warranty of
            merchantability, fitness for a particular purpose, non-infringement, accuracy, or
            uninterrupted operation. We do not warrant that the service will detect all malware, that
            results will be free of error, or that it will meet your requirements. Some jurisdictions
            do not allow certain exclusions, in which case they apply to you only to the extent
            permitted.
          </p>
        </Card>

        <Card>
          <Scale size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">13. Limitation of liability</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            To the fullest extent permitted by law, we are not liable for any indirect, incidental,
            special, consequential, or punitive damages, nor for lost profits, lost revenue, lost
            data, business interruption, or the cost of substitute services — including any of these
            arising from malware that geneav did not detect, or from a file incorrectly flagged as
            infected.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            Our total aggregate liability arising out of or relating to the service is limited to the{" "}
            <strong>greater of the fees you paid us in the twelve months</strong> before the event
            giving rise to the claim, <strong>or USD 100</strong>. These limits apply regardless of
            the legal theory and even if we were advised of the possibility of such damages. Nothing
            in these terms excludes liability that cannot lawfully be excluded, such as for fraud.
          </p>
        </Card>

        <Card>
          <Gavel size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">14. Indemnification</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            You agree to indemnify and hold us harmless from any claim, loss, liability, or expense
            (including reasonable legal fees) arising from your use of the service, your breach of
            these terms, your violation of any law, or your infringement of a third party&apos;s
            rights — including any claim relating to a file you submitted for scanning.
          </p>
        </Card>

        <Card>
          <RefreshCw size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">15. Changes to these terms</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            We may update these terms as the service evolves. When we do, we will revise the
            &ldquo;last updated&rdquo; date above. Material changes will be communicated through the
            website or by email before they take effect. Continuing to use the service after a change
            means you accept the revised terms.
          </p>
        </Card>

        <Card>
          <Mail size={22} className="mb-3 text-brand" aria-hidden />
          <h2 className="m-0 text-lg font-bold text-ink">16. Governing law and contact</h2>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
            These terms are governed by the laws of India, and the courts of India have exclusive
            jurisdiction over any dispute, without regard to conflict-of-law rules. If any provision
            is found unenforceable, the remainder stays in effect. These terms, together with the
            Privacy Policy, are the entire agreement between us regarding the service.
          </p>
          <p className="mt-3 text-[15px] leading-relaxed text-ink-muted">
            Questions about these terms? Email{" "}
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
