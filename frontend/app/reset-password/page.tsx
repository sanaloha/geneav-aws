import ResetPassword from "../components/ResetPassword";

export const metadata = {
  title: "Reset password",
  description: "Set a new password for your geneav account.",
  alternates: { canonical: "/reset-password" },
  // Reset links are single-use and short-lived; keep them out of search results.
  robots: { index: false, follow: false },
};

export default function ResetPasswordPage() {
  return (
    <section className="section">
      <div className="container">
        <h2>Reset password</h2>
        <ResetPassword />
      </div>
    </section>
  );
}
