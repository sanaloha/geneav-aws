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
    { path: "/legal", priority: 0.3, changeFrequency: "monthly" },
  ];
  return routes.map(({ path, priority, changeFrequency }) => ({
    url: `${SITE_URL}${path}`,
    lastModified: now,
    changeFrequency,
    priority,
  }));
}
