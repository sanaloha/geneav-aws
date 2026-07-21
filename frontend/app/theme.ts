/**
 * Design tokens, in TypeScript so the surfaces that cannot use CSS can share them:
 * `opengraph-image.tsx` renders through satori (inline styles only) and
 * `manifest.ts` / `layout.tsx` need raw hex for browser chrome.
 *
 * Contrast ratios below are against `surface` (#FFFFFF) unless noted. Anything
 * used for body text clears WCAG AA (4.5:1); interactive borders clear 1.4.11 (3:1).
 */

export const light = {
  // Surfaces
  surface: "#FFFFFF",
  surfaceSubtle: "#F8FAFC", // alternating bands, dropzone
  surfaceSunken: "#F1F5F9", // meter track, code blocks

  // Text
  text: "#0F172A", // 17.6:1  AAA
  textMuted: "#475569", // 7.6:1   AAA — default secondary text
  textSubtle: "#64748B", // 4.8:1   AA  — placeholders and metadata ONLY

  // Lines. Decorative borders may be low-contrast; control borders may not.
  border: "#E2E8F0", // decorative only (cards, dividers)
  borderControl: "#64748B", // 4.8:1 — inputs, selects, textareas

  // Brand. The old #4f8cff was built for a dark page and is too light on white.
  brand: "#2563EB", // 5.2:1 as text, and white-on-brand also 5.2:1
  brandHover: "#1D4ED8", // 6.9:1
  brandSubtleBg: "#EFF6FF",
  brandSubtleFg: "#1D4ED8",

  // Semantic. The old #35d0a5 is 1.9:1 on white — it cannot carry meaning here,
  // so green is now strictly semantic (clean verdict, active key) and never brand.
  success: "#047857", // 5.5:1 — text and white-on-fill
  successAccent: "#059669", // 3.8:1 — non-text only (meter fill, icon glyphs)
  successBg: "#ECFDF5",
  successBorder: "#A7F3D0",

  danger: "#DC2626", // 4.8:1
  dangerHover: "#B91C1C",
  dangerBg: "#FEF2F2",
  dangerBorder: "#FECACA",

  warning: "#B45309", // 5.0:1
  warningBg: "#FFFBEB",
  warningBorder: "#FDE68A",

  focus: "#2563EB",
} as const;

/**
 * The original dark palette, kept so a future dark mode is a config change
 * rather than a re-derivation. Also the source for the deliberately-dark
 * surfaces that survive in the light design: the CTA band and the OG card.
 */
export const dark = {
  surface: "#0B1020",
  surfaceElevated: "#121A30",
  panel: "#16203A",
  text: "#E7ECF5",
  textMuted: "#9AA7C2",
  border: "#26324F",
  brand: "#4F8CFF",
  accent: "#35D0A5",
} as const;

/** Browser chrome colour (address bar, PWA splash). Must track `light.surface`. */
export const THEME_COLOR = light.surface;
