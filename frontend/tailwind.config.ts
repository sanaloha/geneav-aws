import type { Config } from "tailwindcss";
import { light } from "./app/theme";

/**
 * `container` stays disabled: the Section primitive owns the page width, and a
 * Tailwind-generated `.container` would only be a second way to do the same
 * thing. Preflight is now on — the legacy stylesheet is gone and every element
 * carries explicit classes.
 */
const config: Config = {
  content: ["./app/**/*.{ts,tsx}"],
  corePlugins: {
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
