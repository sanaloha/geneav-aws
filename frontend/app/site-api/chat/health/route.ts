import { backendUrl, demoApiKey, json, relay } from "../../../lib/siteApi";

export const dynamic = "force-dynamic";

/**
 * Whether the assistant can answer free-text questions.
 *
 * With no demo key the widget cannot reach the model at all, so this reports
 * DISABLED — the same shape the backend uses when OpenAI is unconfigured. The
 * widget then keeps its canned suggestion chips and hides the text box, rather
 * than offering an input that could only fail.
 */
export async function GET(): Promise<Response> {
  const key = demoApiKey();
  if (!key) {
    return json({ status: "DISABLED", enabled: false }, 200);
  }

  try {
    const upstream = await fetch(backendUrl("/api/v1/chat/health"), {
      headers: { Authorization: `Bearer ${key}` },
      cache: "no-store",
    });
    return relay(upstream);
  } catch {
    // The widget treats an unreachable health check as available; the send path
    // reports its own errors, and wrongly hiding the input is the worse failure.
    return json({ status: "UNKNOWN", enabled: true }, 200);
  }
}
