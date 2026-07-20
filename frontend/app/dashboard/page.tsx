import Dashboard from "../components/Dashboard";

export const metadata = {
  title: "Dashboard — API keys & usage",
  description:
    "Manage your geneav API keys and track your monthly scan usage against your plan quota.",
  alternates: { canonical: "/dashboard" },
  robots: { index: false, follow: false },
};

export default function DashboardPage() {
  return (
    <section className="section">
      <div className="container">
        <h2>Dashboard</h2>
        <p className="lead">Manage your API keys and track your usage.</p>
        <Dashboard />
      </div>
    </section>
  );
}
