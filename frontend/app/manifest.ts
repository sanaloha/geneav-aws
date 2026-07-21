import type { MetadataRoute } from "next";
import { SITE_DESCRIPTION } from "./site";
import { THEME_COLOR } from "./theme";

// Served at /manifest.webmanifest — basic PWA/install metadata.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "geneav — document malware scanning",
    short_name: "geneav",
    description: SITE_DESCRIPTION,
    start_url: "/",
    display: "standalone",
    background_color: THEME_COLOR,
    theme_color: THEME_COLOR,
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml" }],
  };
}
