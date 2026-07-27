// Central site metadata used across pages, SEO tags, sitemap, and structured data.
export const SITE_NAME = "geneav";
export const SITE_URL = "https://geneav.com";

export const SITE_DESCRIPTION =
  "geneav is an antivirus for documents. Scan files for malware " +
  "through a simple REST API or straight from the website — powered by ClamAV. " +
  "Wire the API into any application.";

export const SITE_TITLE =
  "geneav — antivirus with a document-scanning REST API";

/**
 * Tracker script for the self-hosted Umami instance.
 *
 * A subdomain rather than a path on this origin, because Umami bakes its base
 * path in at build time and serves its collection endpoint at the root — which
 * under the apex would collide with the scan API on /api/*. Only loaded when
 * NEXT_PUBLIC_UMAMI_WEBSITE_ID is set; see layout.tsx.
 */
export const ANALYTICS_SCRIPT_URL = "https://analytics.geneav.com/s.js";

/**
 * The public Azure Marketplace listing for geneav's paid plans.
 *
 * The canonical form is
 * `https://azuremarketplace.microsoft.com/marketplace/apps/<publisherId>.<offerId>`,
 * but the publisher id does not exist until Partner Center onboarding
 * completes — until then this search URL resolves to the listing once it is
 * live and to an empty search before that. Replace it with the canonical URL
 * as soon as the offer is published.
 */
export const MARKETPLACE_LISTING_URL =
  "https://azuremarketplace.microsoft.com/en-us/marketplace/apps?search=geneav";
