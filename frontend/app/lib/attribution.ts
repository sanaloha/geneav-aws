/**
 * First-touch signup attribution.
 *
 * Visitors almost never sign up on the page they land on — they arrive on `/` or
 * a blog post, read, and reach `/login` later. By then the UTM query string and
 * the external referrer are long gone, so both are captured on the very first
 * page view and held until the signup request needs them.
 *
 * Stored in `sessionStorage`, deliberately not a cookie. geneav's privacy page
 * and marketing both claim no tracking cookies and no consent banner; a cookie
 * here would cost a stated differentiator to gain nothing.
 */

const STORAGE_KEY = "geneav.attribution";

export type Attribution = {
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  utmTerm?: string;
  utmContent?: string;
  /** Full URL of the external page that linked here. Same-origin is ignored. */
  referrer?: string;
  /** Path of the first geneav page seen, without host or query. */
  landingPath?: string;
};

/**
 * The backend truncates too, but trimming here keeps a pathological URL from
 * bloating every signup request body.
 */
const UTM_MAX = 128;
const URL_MAX = 512;

function clean(value: string | null, max: number): string | undefined {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return trimmed.slice(0, max);
}

/**
 * Records where this visit came from, unless something is already recorded.
 *
 * Never overwriting is the whole point: once a visitor navigates from a tagged
 * landing page to `/login`, the second call sees no UTM params and would
 * otherwise erase the source that actually earned the signup.
 */
export function captureAttribution(): void {
  if (typeof window === "undefined") return;

  try {
    if (window.sessionStorage.getItem(STORAGE_KEY)) return;

    const params = new URLSearchParams(window.location.search);
    const attribution: Attribution = {
      utmSource: clean(params.get("utm_source"), UTM_MAX),
      utmMedium: clean(params.get("utm_medium"), UTM_MAX),
      utmCampaign: clean(params.get("utm_campaign"), UTM_MAX),
      utmTerm: clean(params.get("utm_term"), UTM_MAX),
      utmContent: clean(params.get("utm_content"), UTM_MAX),
      referrer: externalReferrer(),
      landingPath: clean(window.location.pathname, URL_MAX),
    };

    // A landing path alone says nothing useful about a channel, so a visit with
    // no tags and no external referrer is stored as nothing at all.
    if (!attribution.utmSource && !attribution.utmMedium && !attribution.utmCampaign
      && !attribution.utmTerm && !attribution.utmContent && !attribution.referrer) {
      return;
    }

    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(attribution));
  } catch {
    // Private browsing and storage-blocking extensions both throw here.
    // Attribution is a nice-to-have; nothing user-facing depends on it.
  }
}

/** The stored attribution, or null if there is none or storage is unavailable. */
export function readAttribution(): Attribution | null {
  if (typeof window === "undefined") return null;

  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Attribution) : null;
  } catch {
    return null;
  }
}

/**
 * `document.referrer` is set on internal navigations too, and recording
 * "geneav.com sent them to geneav.com" would drown the real sources.
 */
function externalReferrer(): string | undefined {
  const referrer = document.referrer;
  if (!referrer) return undefined;

  try {
    if (new URL(referrer).host === window.location.host) return undefined;
  } catch {
    return undefined;
  }
  return clean(referrer, URL_MAX);
}
