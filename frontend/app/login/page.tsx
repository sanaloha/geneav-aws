import Dashboard from "../components/Dashboard";
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
      {/* The page heading lives inside Dashboard: it depends on session state,
          which is only known client-side, and this page is a server component. */}
      <Dashboard />
    </Section>
  );
}
