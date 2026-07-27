/**
 * Thin wrapper over the Umami tracker.
 *
 * Umami is self-hosted and cookieless, and the tag only loads when
 * NEXT_PUBLIC_UMAMI_WEBSITE_ID is set at build time — so in local development,
 * and on any deploy that has not been given a website id, `window.umami` is
 * simply absent. Call sites should not have to know that, hence the no-op.
 *
 * Page views are tracked automatically. This is only for the events that are
 * not page views: the ones that tell you whether a visitor actually did
 * anything. See docs/marketing-plan.md §3.1.
 */

declare global {
  interface Window {
    umami?: { track: (event: string, data?: Record<string, unknown>) => void };
  }
}

/**
 * Events worth naming here rather than passing strings at call sites — a typo in
 * an event name is silent and only shows up as a metric that never moves.
 */
export type AnalyticsEvent =
  /** An anonymous scan ran on the marketing site. */
  | "scan-run"
  /** A free-tier account was created. */
  | "signup"
  /** The activation event: a signup went on to create an API key. */
  | "api-key-created"
  /** An Azure Marketplace purchase was activated (billing started). */
  | "marketplace-activated";

export function track(event: AnalyticsEvent, data?: Record<string, unknown>): void {
  if (typeof window === "undefined") return;

  try {
    window.umami?.track(event, data);
  } catch {
    // Analytics must never break the thing it is measuring.
  }
}
