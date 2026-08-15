/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      // --- BankSphere design system -------------------------------------
      // Every color/spacing/radius/shadow value used across the app should
      // come from here, not from arbitrary Tailwind defaults or one-off
      // hex codes in components. See docs/frontend/design-system.md.
      colors: {
        brand: {
          primary: "#0B5FA3",
          "primary-dark": "#073A66",
          "primary-light": "#E7F1FA",
          secondary: "#0E9384",
          "secondary-dark": "#0B675C",
          accent: "#C9962B",
          "accent-light": "#F8EEDA",
        },
        semantic: {
          success: "#15803D",
          "success-light": "#EAF7EE",
          warning: "#B45309",
          "warning-light": "#FCF1E3",
          error: "#DC2626",
          "error-light": "#FDECEC",
          info: "#0284C7",
          "info-light": "#E6F4FB",
        },
        surface: {
          background: "#F5F7FA",
          DEFAULT: "#FFFFFF",
          muted: "#F1F5F9",
          border: "#E2E8F0",
        },
        ink: {
          primary: "#0F172A",
          secondary: "#475569",
          muted: "#94A3B8",
          inverse: "#F8FAFC",
        },
      },
      fontFamily: {
        // A curated native system-font stack rather than a webfont: every
        // OS ships a highly legible, professional UI font already (San
        // Francisco / Segoe UI / Roboto), so this reads as "designed" while
        // avoiding an external font request or a fabricated local font
        // file. See docs/frontend/design-system.md for the reasoning.
        sans: [
          "-apple-system",
          "BlinkMacSystemFont",
          '"Segoe UI"',
          "Roboto",
          '"Helvetica Neue"',
          "Arial",
          '"Noto Sans"',
          "sans-serif",
        ],
      },
      fontSize: {
        display: ["3rem", { lineHeight: "1.1", fontWeight: "700", letterSpacing: "-0.02em" }],
        h1: ["2.25rem", { lineHeight: "1.2", fontWeight: "700", letterSpacing: "-0.015em" }],
        h2: ["1.75rem", { lineHeight: "1.25", fontWeight: "600", letterSpacing: "-0.01em" }],
        h3: ["1.375rem", { lineHeight: "1.3", fontWeight: "600" }],
        body: ["1rem", { lineHeight: "1.6", fontWeight: "400" }],
        "body-sm": ["0.875rem", { lineHeight: "1.55", fontWeight: "400" }],
        caption: ["0.75rem", { lineHeight: "1.4", fontWeight: "400" }],
        label: ["0.8125rem", { lineHeight: "1.3", fontWeight: "500", letterSpacing: "0.02em" }],
      },
      borderRadius: {
        sm: "0.375rem",
        DEFAULT: "0.625rem",
        md: "0.625rem",
        lg: "1rem",
        xl: "1.5rem",
        pill: "999px",
      },
      boxShadow: {
        "elevation-1": "0 1px 2px 0 rgba(15, 23, 42, 0.06)",
        "elevation-2": "0 4px 12px -2px rgba(15, 23, 42, 0.10)",
        "elevation-3": "0 12px 28px -6px rgba(15, 23, 42, 0.16)",
        "elevation-4": "0 24px 48px -12px rgba(15, 23, 42, 0.22)",
      },
      spacing: {
        section: "5rem",
        "section-sm": "3rem",
      },
      maxWidth: {
        content: "72rem",
      },
    },
  },
  plugins: [],
};
