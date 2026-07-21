import Dashboard from "../components/Dashboard";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "Login — API keys & usage",
  description:
    "Sign in to manage your geneav API keys and track your monthly scan usage against your plan quota.",
  alternates: { canonical: "/login" },
  robots: { index: false, follow: false },
};

export default function LoginPage() {
  return (
    <Section>
      <PageHeader
        align="center"
        title="Login"
        lead="Manage your API keys and track your usage."
      />
      <Dashboard />
    </Section>
  );
}
