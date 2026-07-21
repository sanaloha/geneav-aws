import type { Config } from "tailwindcss";
import { light } from "./app/theme";

/**
 * Two core plugins are deliberately disabled:
 *
 * - `preflight` stays OFF until the whole app carries explicit classes. It
 *   zeroes heading sizes/margins and list markers, and this app still has ~47
 *   bare <h2>/<h3>/<ul> elements relying on legacy CSS. Flipping it early would
 *   collapse every heading to body text. Turn it on in the final phase.
 * - `container` collides with the existing `.container` class in globals.css.
 *   The Section primitive replaces that class; until then, Tailwind must not
 *   generate a competing one.
 */
const config: Config = {
  content: ["./app/**/*.{ts,tsx}"],
  corePlugins: {
    preflight: false,
    container: false,
  },
  theme: {
    extend: {
      colors: {
        surface: {
          DEFAULT: light.surface,
          subtle: light.surfaceSubtle,
          sunken: light.surfaceSunken,
        },
        ink: {
          DEFAULT: light.text,
          muted: light.textMuted,
          subtle: light.textSubtle,
        },
        line: {
          DEFAULT: light.border,
          control: light.borderControl,
        },
        brand: {
          DEFAULT: light.brand,
          hover: light.brandHover,
          bg: light.brandSubtleBg,
          fg: light.brandSubtleFg,
        },
        success: {
          DEFAULT: light.success,
          accent: light.successAccent,
          bg: light.successBg,
          border: light.successBorder,
        },
        danger: {
          DEFAULT: light.danger,
          hover: light.dangerHover,
          bg: light.dangerBg,
          border: light.dangerBorder,
        },
        warning: {
          DEFAULT: light.warning,
          bg: light.warningBg,
          border: light.warningBorder,
        },
      },
      fontFamily: {
        sans: ["var(--font-sans)", "ui-sans-serif", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "ui-monospace", "SFMono-Regular", "monospace"],
      },
      borderRadius: {
        card: "14px",
      },
      boxShadow: {
        sm: "0 1px 2px rgba(15, 23, 42, 0.06)",
        md: "0 4px 12px rgba(15, 23, 42, 0.08)",
        lg: "0 12px 32px rgba(15, 23, 42, 0.12)",
      },
      maxWidth: {
        content: "1040px",
      },
    },
  },
  plugins: [],
};

export default config;
