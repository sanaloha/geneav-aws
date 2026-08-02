// Central site metadata used across pages, SEO tags, sitemap, and structured data.
export const SITE_NAME = "geneav";

/**
 * The public origin this build is served from.
 *
 * Derived from NEXT_PUBLIC_API_BASE_URL rather than hardcoded, because the site
 * and the API share one origin by construction: Caddy routes /api/* to the
 * backend and everything else to the frontend on the same hostname, so there is
 * only ever one public origin per deployment.
 *
 * This was hardcoded to https://geneav.com until 2 Aug 2026, which meant a
 * deployment on any other hostname advertised canonical and OpenGraph URLs
 * pointing at a different site — and loaded its analytics tracker from one too.
 *
 * Like every NEXT_PUBLIC_* value this is inlined at IMAGE BUILD time, so a
 * hostname change needs a rebuild, not a restart. See the README.
 */
function deriveSiteUrl(apiBase: string): string {
  try {
    const url = new URL(apiBase);
    // Local development runs the API on :8080 and the site on :3000. Everywhere
    // else they are the same origin, so only this case needs correcting.
    if (url.hostname === "localhost" || url.hostname === "127.0.0.1") {
      return "http://localhost:3000";
    }
    return url.origin;
  } catch {
    // A malformed value must not fail the build; fall back to the public site.
    return "https://geneav.com";
  }
}

export const SITE_URL = deriveSiteUrl(
  process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080",
);

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
 *
 * Derived from SITE_URL to match docker-compose.prod.yml, which sets Caddy's
 * analytics hostname to `analytics.${GENEAV_HOST}`. Hardcoding it meant a
 * deployment on a second hostname fetched its tracker from the original site —
 * a cross-origin request to a host this deployment does not control, and one
 * that simply fails when that host is down.
 */
export const ANALYTICS_SCRIPT_URL = (() => {
  try {
    const url = new URL(SITE_URL);
    return `${url.protocol}//analytics.${url.host}/s.js`;
  } catch {
    return "https://analytics.geneav.com/s.js";
  }
})();

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
