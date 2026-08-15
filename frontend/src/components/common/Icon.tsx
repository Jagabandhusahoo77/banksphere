import type { SVGProps } from "react";

/**
 * A small, self-authored line-icon set (24x24, stroke = currentColor) so
 * BankSphere doesn't depend on an icon-library package. Add new names here
 * rather than inlining one-off <svg> markup in a page/component.
 */
const ICON_PATHS: Record<string, JSX.Element> = {
  menu: (
    <>
      <line x1="3" y1="6" x2="21" y2="6" />
      <line x1="3" y1="12" x2="21" y2="12" />
      <line x1="3" y1="18" x2="21" y2="18" />
    </>
  ),
  close: (
    <>
      <line x1="6" y1="6" x2="18" y2="18" />
      <line x1="18" y1="6" x2="6" y2="18" />
    </>
  ),
  "chevron-down": <polyline points="6 9 12 15 18 9" />,
  "chevron-right": <polyline points="9 6 15 12 9 18" />,
  "chevron-left": <polyline points="15 6 9 12 15 18" />,
  "arrow-right": (
    <>
      <line x1="4" y1="12" x2="20" y2="12" />
      <polyline points="13 5 20 12 13 19" />
    </>
  ),
  "arrow-up-right": (
    <>
      <line x1="7" y1="17" x2="17" y2="7" />
      <polyline points="8 7 17 7 17 16" />
    </>
  ),
  "arrow-down-left": (
    <>
      <line x1="17" y1="7" x2="7" y2="17" />
      <polyline points="16 17 7 17 7 8" />
    </>
  ),
  check: <polyline points="5 13 10 18 19 7" />,
  "check-circle": (
    <>
      <circle cx="12" cy="12" r="9" />
      <polyline points="8 12.5 11 15.5 16 9" />
    </>
  ),
  "alert-circle": (
    <>
      <circle cx="12" cy="12" r="9" />
      <line x1="12" y1="7.5" x2="12" y2="13" />
      <circle cx="12" cy="16.5" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c0-3.9 3.1-7 7-7s7 3.1 7 7" />
    </>
  ),
  users: (
    <>
      <circle cx="9" cy="8.5" r="3" />
      <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
      <path d="M15.5 5.5a3 3 0 0 1 0 6" />
      <path d="M17.5 14.2c2.6.5 4.5 2.8 4.5 5.8" />
    </>
  ),
  "log-out": (
    <>
      <path d="M9 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3" />
      <polyline points="15 8 19 12 15 16" />
      <line x1="19" y1="12" x2="9" y2="12" />
    </>
  ),
  home: (
    <>
      <path d="M4 11l8-7 8 7" />
      <path d="M6 10v9a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-9" />
    </>
  ),
  wallet: (
    <>
      <rect x="3.5" y="6.5" width="17" height="12" rx="2" />
      <path d="M16 12.5h2.5" />
      <path d="M3.5 9.5h17" />
    </>
  ),
  list: (
    <>
      <line x1="8" y1="6" x2="21" y2="6" />
      <line x1="8" y1="12" x2="21" y2="12" />
      <line x1="8" y1="18" x2="21" y2="18" />
      <line x1="3.5" y1="6" x2="3.51" y2="6" />
      <line x1="3.5" y1="12" x2="3.51" y2="12" />
      <line x1="3.5" y1="18" x2="3.51" y2="18" />
    </>
  ),
  "credit-card": (
    <>
      <rect x="3" y="6" width="18" height="12" rx="2" />
      <line x1="3" y1="10" x2="21" y2="10" />
    </>
  ),
  "trending-up": (
    <>
      <polyline points="4 16 10 10 14 14 20 6" />
      <polyline points="14 6 20 6 20 12" />
    </>
  ),
  headset: (
    <>
      <path d="M4 13a8 8 0 0 1 16 0" />
      <rect x="3" y="13" width="4" height="6" rx="1.5" />
      <rect x="17" y="13" width="4" height="6" rx="1.5" />
      <path d="M19 19v1a3 3 0 0 1-3 3h-3" />
    </>
  ),
  eye: (
    <>
      <path d="M2 12s3.6-6.5 10-6.5 10 6.5 10 6.5-3.6 6.5-10 6.5S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
    </>
  ),
  "eye-off": (
    <>
      <path d="M3 3l18 18" />
      <path d="M10.6 5.7A9.9 9.9 0 0 1 12 5.5c6.4 0 10 6.5 10 6.5a17.6 17.6 0 0 1-3.2 4" />
      <path d="M6.5 7.8A17.5 17.5 0 0 0 2 12s3.6 6.5 10 6.5a9.6 9.6 0 0 0 4.2-.9" />
      <path d="M9.5 12a2.5 2.5 0 0 0 3.6 2.2" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.2" y2="16.2" />
    </>
  ),
  mail: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M3.5 6.5l8.5 6.5 8.5-6.5" />
    </>
  ),
  phone: <path d="M6.5 3.5h3l1.5 4-2 1.5a13 13 0 0 0 6 6l1.5-2 4 1.5v3a2 2 0 0 1-2.2 2A17 17 0 0 1 4.5 5.7a2 2 0 0 1 2-2.2z" />,
  "shield-check": (
    <>
      <path d="M12 3l7 3v6c0 5-3.5 7.5-7 9-3.5-1.5-7-4-7-9V6z" />
      <polyline points="9 12 11.2 14.2 15.5 9.5" />
    </>
  ),
  plus: (
    <>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </>
  ),
  minus: <line x1="5" y1="12" x2="19" y2="12" />,
  "external-link": (
    <>
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
      <polyline points="15 3 21 3 21 9" />
      <line x1="10" y1="14" x2="21" y2="3" />
    </>
  ),
  building: (
    <>
      <rect x="5" y="3.5" width="14" height="17" rx="1" />
      <line x1="9" y1="7.5" x2="9" y2="7.51" />
      <line x1="15" y1="7.5" x2="15" y2="7.51" />
      <line x1="9" y1="11.5" x2="9" y2="11.51" />
      <line x1="15" y1="11.5" x2="15" y2="11.51" />
      <line x1="9" y1="15.5" x2="9" y2="15.51" />
      <line x1="15" y1="15.5" x2="15" y2="15.51" />
      <path d="M10 20.5v-4h4v4" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <polyline points="12 7 12 12 15.5 14" />
    </>
  ),
  spinner: (
    <>
      <path d="M12 3a9 9 0 1 0 9 9" />
    </>
  ),
  calendar: (
    <>
      <rect x="3.5" y="5" width="17" height="15" rx="2" />
      <line x1="3.5" y1="9.5" x2="20.5" y2="9.5" />
      <line x1="8" y1="3" x2="8" y2="7" />
      <line x1="16" y1="3" x2="16" y2="7" />
    </>
  ),
  tag: (
    <>
      <path d="M12.5 3.5h5a1 1 0 0 1 1 1v5a1 1 0 0 1-.3.7l-9 9a1 1 0 0 1-1.4 0l-5.7-5.7a1 1 0 0 1 0-1.4l9-9a1 1 0 0 1 .7-.3z" />
      <circle cx="16" cy="8" r="1.3" fill="currentColor" stroke="none" />
    </>
  ),
  umbrella: (
    <>
      <path d="M4 12a8 8 0 0 1 16 0z" />
      <line x1="12" y1="12" x2="12" y2="19.5" />
      <path d="M12 19.5a2 2 0 0 1-4 0" />
    </>
  ),
  percent: (
    <>
      <line x1="5" y1="19" x2="19" y2="5" />
      <circle cx="7.5" cy="7.5" r="2.5" />
      <circle cx="16.5" cy="16.5" r="2.5" />
    </>
  ),
  car: (
    <>
      <path d="M4 16v-3.5l2-4.5a2 2 0 0 1 1.8-1h8.4a2 2 0 0 1 1.8 1l2 4.5V16" />
      <line x1="4" y1="16" x2="20" y2="16" />
      <circle cx="7.5" cy="17.5" r="1.5" />
      <circle cx="16.5" cy="17.5" r="1.5" />
    </>
  ),
  "graduation-cap": (
    <>
      <path d="M2 9.5 12 5l10 4.5-10 4.5z" />
      <path d="M6.5 11.5v4c0 1.4 2.5 2.5 5.5 2.5s5.5-1.1 5.5-2.5v-4" />
      <line x1="20.5" y1="9.5" x2="20.5" y2="15" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="9" />
      <ellipse cx="12" cy="12" rx="4" ry="9" />
      <line x1="3" y1="12" x2="21" y2="12" />
    </>
  ),
  star: <path d="M12 3.5l2.6 5.4 5.9.7-4.3 4.1 1.1 5.9L12 16.7l-5.3 2.9 1.1-5.9-4.3-4.1 5.9-.7z" />,
  upload: (
    <>
      <path d="M12 15.5V4" />
      <polyline points="7.5 8.5 12 4 16.5 8.5" />
      <path d="M4.5 15.5V19a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3.5" />
    </>
  ),
  file: (
    <>
      <path d="M6 3.5h8l4.5 4.5V19a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 19V5A1.5 1.5 0 0 1 6 3.5z" />
      <polyline points="14 3.5 14 8 18.5 8" />
    </>
  ),
};

export type IconName = keyof typeof ICON_PATHS;

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName;
  size?: number;
}

export default function Icon({ name, size = 20, className, ...rest }: IconProps) {
  const content = ICON_PATHS[name];
  if (!content) return null;

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className}
      {...rest}
    >
      {content}
    </svg>
  );
}
