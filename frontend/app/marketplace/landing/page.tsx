import MarketplaceLanding from "../../components/MarketplaceLanding";
import Section from "../../ui/Section";

export const metadata = {
  title: "Set up your subscription — Azure Marketplace",
  description:
    "Activate and manage your geneav subscription purchased through the Azure Marketplace.",
  alternates: { canonical: "/marketplace/landing" },
  // Never index: buyers arrive here with a purchase token in the URL.
  robots: { index: false, follow: false },
};

/**
 * Landing page registered in the offer's Partner Center Technical
 * Configuration. Microsoft sends buyers here after purchase (with a `token`
 * query parameter) and again when they choose "Configure account" on an
 * active subscription — so it must stay reachable 24/7.
 */
export default function MarketplaceLandingPage() {
  return (
    <Section>
      <MarketplaceLanding />
    </Section>
  );
}
