import type { MetadataRoute } from "next";
import { SITE_URL } from "./site";

// Served at /sitemap.xml — enumerates every crawlable page.
export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  const routes: { path: string; priority: number; changeFrequency: "monthly" | "weekly" }[] = [
    { path: "/", priority: 1.0, changeFrequency: "weekly" },
    { path: "/features", priority: 0.8, changeFrequency: "monthly" },
    { path: "/developers", priority: 0.9, changeFrequency: "monthly" },
    { path: "/about", priority: 0.6, changeFrequency: "monthly" },
    // Buyers reach /security straight from questionnaires and vendor reviews,
    // so it earns a higher priority than the other trust pages.
    { path: "/security", priority: 0.5, changeFrequency: "monthly" },
    { path: "/terms", priority: 0.3, changeFrequency: "monthly" },
    { path: "/legal", priority: 0.3, changeFrequency: "monthly" },
    { path: "/privacy", priority: 0.3, changeFrequency: "monthly" },
  ];
  return routes.map(({ path, priority, changeFrequency }) => ({
    url: `${SITE_URL}${path}`,
    lastModified: now,
    changeFrequency,
    priority,
  }));
}
