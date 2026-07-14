import type { Metadata, Viewport } from "next";
import "./globals.css";
import Nav from "./components/Nav";
import {
  SITE_NAME,
  SITE_URL,
  SITE_TITLE,
  SITE_DESCRIPTION,
  REPO_URL,
  LICENSE_URL,
} from "./site";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: SITE_TITLE,
    template: "%s · geneav",
  },
  description: SITE_DESCRIPTION,
  applicationName: SITE_NAME,
  keywords: [
    "open source antivirus",
    "document malware scanning",
    "virus scan API",
    "ClamAV REST API",
    "file scanning API",
    "scan PDF for malware",
    "self-hosted antivirus",
    "malware detection API",
    "MIT licensed antivirus",
    "document security",
  ],
  authors: [{ name: SITE_NAME, url: REPO_URL }],
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
  themeColor: "#0b1020",
};

// Structured data so search engines understand geneav is free, open-source software.
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
      license: LICENSE_URL,
      codeRepository: REPO_URL,
      offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
    },
    {
      "@type": "Organization",
      name: SITE_NAME,
      url: SITE_URL,
      sameAs: [REPO_URL],
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
    <html lang="en">
      <body>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
        <Nav />
        <main>{children}</main>
        <footer className="footer">
          <div className="container footer-inner">
            <span>© {new Date().getFullYear()} geneav · Document malware scanning · Powered by ClamAV</span>
            <span>
              Open source (MIT) ·{" "}
              <a href={REPO_URL} target="_blank" rel="noreferrer">
                GitHub
              </a>{" "}
              ·{" "}
              <a href={LICENSE_URL} target="_blank" rel="noreferrer">
                License
              </a>
            </span>
          </div>
        </footer>
      </body>
    </html>
  );
}
