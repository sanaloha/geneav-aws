import {
  backendUrl,
  demoApiKey,
  relay,
  upstreamUnreachable,
} from "../../../lib/siteApi";

export const dynamic = "force-dynamic";

/**
 * The chat widget's one-click question chips.
 *
 * Unlike /site-api/chat this answers 200 with an empty list when no demo key is
 * configured: the widget treats chips as optional and hides them, which is a
 * better fallback than an error banner on a page nobody asked to chat on.
 */
export async function GET(): Promise<Response> {
  const key = demoApiKey();
  if (!key) {
    return new Response("[]", {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }

  try {
    const upstream = await fetch(backendUrl("/api/v1/chat/suggestions"), {
      headers: { Authorization: `Bearer ${key}` },
      // The list only changes on deploy and the backend already sets a public
      // max-age, which relay() passes through to the browser.
      cache: "no-store",
    });
    return relay(upstream);
  } catch {
    return upstreamUnreachable("assistant");
  }
}
