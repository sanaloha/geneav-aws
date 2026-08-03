import {
  backendUrl,
  demoApiKey,
  demoUnavailable,
  json,
  relay,
  upstreamUnreachable,
} from "../../lib/siteApi";

// Reached from ScanForm on the homepage and /developers. Never prerendered or
// cached: every call is a live scan.
export const dynamic = "force-dynamic";

/**
 * Largest file the demo will forward.
 *
 * Caddy caps /api/* uploads at 30MB, but this route is served by the frontend and
 * so falls outside that block; without a limit of its own an unbounded body would
 * be buffered below. 30MB keeps the demo and the documented API limit in step.
 */
const MAX_UPLOAD_BYTES = 30 * 1024 * 1024;

export async function POST(request: Request): Promise<Response> {
  const key = demoApiKey();
  if (!key) {
    return demoUnavailable();
  }

  const declared = Number(request.headers.get("content-length") ?? 0);
  if (declared > MAX_UPLOAD_BYTES) {
    return json({ message: "File too large. The demo accepts uploads up to 30MB." }, 413);
  }

  // The multipart body is buffered rather than streamed: streaming a request body
  // through fetch needs duplex half-support end to end, and at a 30MB ceiling —
  // bounded further by the backend's scan concurrency permits — buffering is the
  // sturdier trade. Content-Type is forwarded verbatim because it carries the
  // multipart boundary the backend needs to parse the part.
  const body = await request.arrayBuffer();
  if (body.byteLength > MAX_UPLOAD_BYTES) {
    return json({ message: "File too large. The demo accepts uploads up to 30MB." }, 413);
  }

  try {
    const upstream = await fetch(backendUrl("/api/v1/scan"), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${key}`,
        "content-type": request.headers.get("content-type") ?? "application/octet-stream",
      },
      body,
      cache: "no-store",
    });
    return relay(upstream);
  } catch {
    return upstreamUnreachable("scan");
  }
}
