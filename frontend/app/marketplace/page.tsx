import { ArrowRight, BadgeCheck, CreditCard, ShieldCheck, Wallet } from "lucide-react";
import { ButtonLink } from "../ui/Button";
import Card from "../ui/Card";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";
import { MARKETPLACE_LISTING_URL } from "../site";

export const metadata = {
  title: "Get geneav on the Azure Marketplace",
  description:
    "Purchase geneav's Starter, Pro, and Scale plans through the Microsoft Azure Marketplace — billed by Microsoft on your Azure invoice, with a 30-day free trial on Pro.",
  alternates: { canonical: "/marketplace" },
};

/**
 * Every claim on this page must be true of the current build — the same house
 * rule as the home page. Microsoft is the merchant of record for marketplace
 * purchases; do not describe billing as ours.
 */
const points = [
  {
    icon: CreditCard,
    title: "Billed by Microsoft",
    body: "The subscription lands on your existing Azure invoice. No new vendor onboarding, no card details shared with us.",
  },
  {
    icon: Wallet,
    title: "Counts toward Azure commitments",
    body: "An eligible marketplace purchase can draw down your organisation's Microsoft Azure Consumption Commitment.",
  },
  {
    icon: BadgeCheck,
    title: "30-day free trial on Pro",
    body: "Try the full Pro quota before paying. Cancel inside the trial and the subscription never converts.",
  },
  {
    icon: ShieldCheck,
    title: "Same API, same account",
    body: "A marketplace purchase raises the quota on your geneav account. Keys, docs, and endpoints stay exactly the same.",
  },
];

const steps = [
  "Find geneav on the Azure Marketplace and pick Starter, Pro, or Scale.",
  "Complete the purchase — Microsoft confirms the price and billing term.",
  "You are redirected to geneav to sign in with Microsoft and activate.",
  "Your plan quota is live; manage keys and usage from the dashboard.",
];

export default function MarketplacePage() {
  return (
    <>
      <Section size="hero">
        <PageHeader
          title="Get geneav on the Azure Marketplace"
          lead="Buy Starter, Pro, or Scale through Microsoft — billed on your Azure invoice, activated in minutes."
        />
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <ButtonLink
            href={MARKETPLACE_LISTING_URL}
            variant="primary"
            size="lg"
            target="_blank"
            rel="noopener noreferrer"
          >
            View the listing <ArrowRight size={18} aria-hidden />
          </ButtonLink>
          <ButtonLink href="/#pricing" variant="secondary" size="lg">
            Compare plans
          </ButtonLink>
        </div>
      </Section>

      <Section tone="subtle">
        <div className="grid gap-5 sm:grid-cols-2">
          {points.map(({ icon: Icon, title, body }) => (
            <Card key={title}>
              <Icon size={22} className="text-brand" aria-hidden />
              <h2 className="mb-1.5 mt-3 text-base font-bold text-ink">{title}</h2>
              <p className="m-0 text-sm leading-relaxed text-ink-muted">{body}</p>
            </Card>
          ))}
        </div>
      </Section>

      <Section>
        <PageHeader level="h2" align="center" title="How a purchase works" />
        <ol className="mx-auto mt-8 flex max-w-xl list-none flex-col gap-4 p-0">
          {steps.map((step, i) => (
            <li key={step} className="flex items-start gap-3.5">
              <span
                className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand text-sm font-bold text-white"
                aria-hidden
              >
                {i + 1}
              </span>
              <p className="m-0 pt-0.5 text-[15px] leading-relaxed text-ink-muted">{step}</p>
            </li>
          ))}
        </ol>
        <p className="mx-auto mt-8 max-w-xl text-center text-[13px] leading-relaxed text-ink-subtle">
          Marketplace subscriptions are sold and billed by Microsoft under the Microsoft Customer
          Agreement; cancellation and refunds run through the Azure portal. The free tier —
          500 scans a month — stays right here, no marketplace required.
        </p>
      </Section>
    </>
  );
}
