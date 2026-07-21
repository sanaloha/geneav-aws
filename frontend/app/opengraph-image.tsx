import { ImageResponse } from "next/og";
import { SITE_TITLE } from "./site";
import { dark } from "./theme";

// Dynamically generated social-share card (Open Graph + Twitter) at build/request time.
//
// This card stays DARK even though the site is light: a dark card stands out in
// the light feeds it is shown in (Slack, X, LinkedIn). That is deliberate — do
// not "fix" it to match the site.
//
// Two constraints when editing: satori supports only a subset of CSS and cannot
// use Tailwind classes, and every div with more than one child needs an explicit
// `display: flex`. Errors here surface at request time, not build time — check
// /opengraph-image in a browser after any change.
export const alt = SITE_TITLE;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// Generate at request time (not during the build) so the OG font loads from the
// container's filesystem rather than being prerendered on the build host.
export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          height: "100%",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          padding: "80px",
          background: `linear-gradient(135deg, ${dark.surface} 0%, ${dark.panel} 100%)`,
          color: dark.text,
          fontFamily: "sans-serif",
        }}
      >
        <div
          style={{
            display: "flex",
            fontSize: 30,
            fontWeight: 700,
            letterSpacing: 2,
            color: dark.accent,
          }}
        >
          DOCUMENT MALWARE SCANNING
        </div>
        <div style={{ display: "flex", fontSize: 110, fontWeight: 800, marginTop: 24 }}>
          <span>gene</span>
          <span style={{ color: dark.brand }}>av</span>
        </div>
        <div style={{ display: "flex", fontSize: 46, color: dark.textMuted, marginTop: 12 }}>
          Scan every document for malware.
        </div>
        <div style={{ display: "flex", fontSize: 30, color: dark.textMuted, marginTop: 48 }}>
          REST API · powered by ClamAV · geneav.com
        </div>
      </div>
    ),
    { ...size }
  );
}
