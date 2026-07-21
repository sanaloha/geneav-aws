import ResetPassword from "../components/ResetPassword";
import PageHeader from "../ui/PageHeader";
import Section from "../ui/Section";

export const metadata = {
  title: "Reset password",
  description: "Set a new password for your geneav account.",
  alternates: { canonical: "/reset-password" },
  // Reset links are single-use and short-lived; keep them out of search results.
  robots: { index: false, follow: false },
};

export default function ResetPasswordPage() {
  return (
    <Section>
      <PageHeader align="center" title="Reset password" />
      <ResetPassword />
    </Section>
  );
}
