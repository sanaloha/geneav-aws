import type { Metadata, Viewport } from "next";
import { ShieldCheck } from "lucide-react";
import { Inter, JetBrains_Mono } from "next/font/google";
import Link from "next/link";
import "./globals.css";
import Nav from "./components/Nav";
import ChatWidget from "./components/ChatWidget";
import { SITE_NAME, SITE_URL, SITE_TITLE, SITE_DESCRIPTION } from "./site";
import { THEME_COLOR } from "./theme";

// Self-hosted by next/font: no external request, no layout shift on load.
const inter = Inter({ subsets: ["latin"], display: "swap", variable: "--font-sans" });
const mono = JetBrains_Mono({ subsets: ["latin"], display: "swap", variable: "--font-mono" });

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: SITE_TITLE,
    template: "%s · geneav",
  },
  description: SITE_DESCRIPTION,
  applicationName: SITE_NAME,
  keywords: [
    "document malware scanning",
    "virus scan API",
    "ClamAV REST API",
    "file scanning API",
    "scan PDF for malware",
    "self-hosted antivirus",
    "malware detection API",
    "document security",
  ],
  authors: [{ name: SITE_NAME, url: SITE_URL }],
  creator: SITE_NAME,
  publisher: SITE_NAME,
  category: "technology",
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    url: SITE_URL,
    siteName: SITE_NAME,
    title: SITE_TITLE,
    description: SITE_DESCRIPTION,
    locale: "en_US",
  },
  twitter: {
    card: "summary_large_image",
    title: SITE_TITLE,
    description: SITE_DESCRIPTION,
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
      "max-video-preview": -1,
    },
  },
};

export const viewport: Viewport = {
  themeColor: THEME_COLOR,
};

// Structured data so search engines understand what geneav is.
const jsonLd = {
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "SoftwareApplication",
      name: SITE_NAME,
      description: SITE_DESCRIPTION,
      url: SITE_URL,
      applicationCategory: "SecurityApplication",
      operatingSystem: "Any",
      isAccessibleForFree: true,
      offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
    },
    {
      "@type": "Organization",
      name: SITE_NAME,
      url: SITE_URL,
    },
    {
      "@type": "WebSite",
      name: SITE_NAME,
      url: SITE_URL,
      description: SITE_DESCRIPTION,
    },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${inter.variable} ${mono.variable}`}>
      <body>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
        <Nav />
        <main>{children}</main>
        <footer className="border-t border-line bg-surface-subtle">
          <div className="mx-auto max-w-content px-5 py-12">
            <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
              <div>
                <div className="flex items-center gap-2 text-lg font-extrabold tracking-tight text-ink">
                  <span className="flex h-6 w-6 items-center justify-center rounded-md bg-brand text-white">
                    <ShieldCheck size={14} aria-hidden />
                  </span>
                  gene<span className="-ml-[3px] text-brand">av</span>
                </div>
                <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink-muted">
                  Antivirus for documents. Scan uploads for malware through a simple REST API.
                </p>
              </div>

              {[
                {
                  heading: "Product",
                  items: [
                    { href: "/features", label: "Features" },
                    { href: "/#pricing", label: "Pricing" },
                    { href: "/login", label: "Sign in" },
                  ],
                },
                {
                  heading: "Developers",
                  items: [
                    { href: "/developers", label: "API reference" },
                    { href: "/docs", label: "Swagger UI" },
                    { href: "/api-docs", label: "OpenAPI document" },
                  ],
                },
                {
                  heading: "Company",
                  items: [
                    { href: "/about", label: "About" },
                    { href: "/legal", label: "Legal & attribution" },
                    { href: "mailto:admin@geneav.com", label: "Contact" },
                  ],
                },
              ].map((col) => (
                <div key={col.heading}>
                  <h2 className="m-0 text-[13px] font-extrabold uppercase tracking-wider text-ink">
                    {col.heading}
                  </h2>
                  <ul className="mt-3 list-none space-y-2 p-0">
                    {col.items.map((item) => (
                      <li key={item.href}>
                        <Link
                          href={item.href}
                          className="text-sm text-ink-muted no-underline hover:text-brand"
                        >
                          {item.label}
                        </Link>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>

            <div className="mt-10 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-6 text-[13px] text-ink-muted">
              <span>© {new Date().getFullYear()} geneav · Document malware scanning</span>
              <span>Powered by ClamAV (GNU GPL v2)</span>
            </div>
          </div>
        </footer>
        <ChatWidget />
      </body>
    </html>
  );
}
