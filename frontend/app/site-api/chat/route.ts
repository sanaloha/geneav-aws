import {
  backendUrl,
  demoApiKey,
  demoUnavailable,
  relay,
  upstreamUnreachable,
} from "../../lib/siteApi";

// Reached from ChatWidget, which is mounted on every page.
export const dynamic = "force-dynamic";

/** Guards against a client posting an unbounded conversation through the proxy. */
const MAX_BODY_BYTES = 256 * 1024;

export async function POST(request: Request): Promise<Response> {
  const key = demoApiKey();
  if (!key) {
    return demoUnavailable();
  }

  const body = await request.text();
  if (body.length > MAX_BODY_BYTES) {
    return new Response(JSON.stringify({ message: "Message too long." }), {
      status: 413,
      headers: { "content-type": "application/json" },
    });
  }

  try {
    const upstream = await fetch(backendUrl("/api/v1/chat"), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${key}`,
        "content-type": "application/json",
      },
      body,
      cache: "no-store",
    });
    return relay(upstream);
  } catch {
    return upstreamUnreachable("assistant");
  }
}
