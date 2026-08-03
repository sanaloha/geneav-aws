/**
 * Server-side plumbing for the calls the website makes to the scan API on a
 * visitor's behalf.
 *
 * The API requires an `Authorization: Bearer gav_live_…` key on /scan and /chat
 * (see ApiKeyAuthFilter). The homepage demo scanner and the chat widget are used
 * by visitors who have no account, so the browser cannot supply one — and shipping
 * a key to the browser would publish it. Instead those components call same-origin
 * routes under /site-api/*, and these helpers attach a dedicated demo key here on
 * the server, where the value stays inside the container.
 *
 * Every module under app/site-api/ is a route handler, so this file only ever runs
 * on the server. Do not import it from a "use client" component: that would inline
 * the key into the client bundle.
 */

/** Local development, where the API runs on :8080 and the site on :3000. */
const FALLBACK_BASE = "http://localhost:8080";

/**
 * Where the server reaches the backend.
 *
 * Deliberately separate from NEXT_PUBLIC_API_BASE_URL. That value is the *public*
 * origin, baked into the client bundle at image-build time; this one is read at
 * runtime and in production points straight at the backend container
 * (http://backend:8080), so a demo scan does not leave the docker network, pay for
 * TLS, or consume the visitor-facing rate-limit budget at Caddy.
 */
export function backendUrl(path: string): string {
  const base =
    process.env.GENEAV_API_INTERNAL_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL ||
    FALLBACK_BASE;
  return `${base.replace(/\/+$/, "")}${path}`;
}

/**
 * The demo account's API key, or null when the deployment has not configured one.
 *
 * Unset is a legitimate state — a fresh box has an empty database and therefore no
 * key to configure yet — so the routes degrade to an honest 503 rather than
 * forwarding a keyless request and surfacing the API's 401 as if the visitor had
 * done something wrong.
 */
export function demoApiKey(): string | null {
  const key = process.env.GENEAV_DEMO_API_KEY?.trim();
  return key ? key : null;
}

/** The 503 served when no demo key is configured. */
export function demoUnavailable(): Response {
  return json(
    {
      message:
        "The live demo is not configured on this deployment. Sign up for an API key to scan a file.",
    },
    503,
  );
}

export function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/**
 * Relays an upstream response to the browser as-is.
 *
 * The status and body are passed through untouched so the components keep
 * rendering the API's own error messages — a 402 "monthly quota reached" or a 429
 * still reads correctly in the widget. Only hop-by-hop and auth-related headers
 * are dropped; Retry-After and Cache-Control are kept because the callers use them.
 */
export async function relay(upstream: Response): Promise<Response> {
  const headers = new Headers();
  const contentType = upstream.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  for (const name of ["retry-after", "cache-control"]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  return new Response(await upstream.arrayBuffer(), {
    status: upstream.status,
    headers,
  });
}

/** The upstream-unreachable 502, shared by every route here. */
export function upstreamUnreachable(what: string): Response {
  return json({ message: `Could not reach the ${what} service. Please try again shortly.` }, 502);
}
