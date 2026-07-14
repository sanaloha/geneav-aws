import { ImageResponse } from "next/og";
import { SITE_TITLE } from "./site";

// Dynamically generated social-share card (Open Graph + Twitter) at build/request time.
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
          background: "linear-gradient(135deg, #0b1020 0%, #16203a 100%)",
          color: "#e7ecf5",
          fontFamily: "sans-serif",
        }}
      >
        <div
          style={{
            display: "flex",
            fontSize: 30,
            fontWeight: 700,
            letterSpacing: 2,
            color: "#35d0a5",
          }}
        >
          OPEN SOURCE · MIT LICENSED
        </div>
        <div style={{ display: "flex", fontSize: 110, fontWeight: 800, marginTop: 24 }}>
          <span>gene</span>
          <span style={{ color: "#4f8cff" }}>av</span>
        </div>
        <div style={{ display: "flex", fontSize: 46, color: "#9aa7c2", marginTop: 12 }}>
          Scan every document for malware.
        </div>
        <div style={{ display: "flex", fontSize: 30, color: "#9aa7c2", marginTop: 48 }}>
          REST API · powered by ClamAV · geneav.com
        </div>
      </div>
    ),
    { ...size }
  );
}
