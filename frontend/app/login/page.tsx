import Dashboard from "../components/Dashboard";

export const metadata = {
  title: "Login — API keys & usage",
  description:
    "Sign in to manage your geneav API keys and track your monthly scan usage against your plan quota.",
  alternates: { canonical: "/login" },
  robots: { index: false, follow: false },
};

export default function LoginPage() {
  return (
    <section className="section">
      <div className="container">
        <h2>Login</h2>
        <p className="lead">Manage your API keys and track your usage.</p>
        <Dashboard />
      </div>
    </section>
  );
}
