import type { Metadata } from "next";
import "./globals.css";
import Nav from "./components/Nav";

export const metadata: Metadata = {
  title: "geneav — antivirus with a document-scanning REST API",
  description:
    "geneav scans your documents for malware and exposes a simple REST API so you can plug virus scanning into any application.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Nav />
        <main>{children}</main>
        <footer className="footer">
          <div className="container">
            © {new Date().getFullYear()} geneav · Document malware scanning · Powered by ClamAV
          </div>
        </footer>
      </body>
    </html>
  );
}
