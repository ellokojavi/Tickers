import type { JSX } from "preact";

/**
 * Inline SVG rather than emoji: emoji render in each platform's own colours and
 * style, which reads as decoration pasted into an otherwise restrained
 * interface. These inherit currentColor and match the Android app's set.
 */
const svg = (children: JSX.Element, label?: string): JSX.Element => (
  <svg
    width="24" height="24" viewBox="0 0 24 24"
    role={label === undefined ? "presentation" : "img"}
    aria-label={label} aria-hidden={label === undefined}
    fill="none" stroke="currentColor" stroke-width="1.8"
    stroke-linecap="round" stroke-linejoin="round"
  >{children}</svg>
);

export const ChartIcon = () => svg(<polyline points="3,17 9,11 13,15 21,6" />);

export const CalculatorIcon = () => svg(
  <>
    <rect x="4.5" y="2.5" width="15" height="19" rx="2.5" />
    <rect x="7.8" y="5.8" width="8.4" height="3.4" rx="1" stroke-width="1.5" />
    {[13, 17].map((cy) => [8.6, 12, 15.4].map((cx) => (
      <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="1.05" fill="currentColor" stroke="none" />
    )))}
  </>,
);

export const BankIcon = () => svg(
  <>
    <path d="M3 9.5 12 4l9 5.5" />
    <path d="M5.5 11v6M12 11v6M18.5 11v6" />
    <path d="M3.5 20h17" />
  </>,
);

export const ShareIcon = () => svg(
  <>
    <circle cx="18" cy="5" r="2.6" /><circle cx="6" cy="12" r="2.6" /><circle cx="18" cy="19" r="2.6" />
    <path d="M8.4 10.8 15.6 6.4M8.4 13.2l7.2 4.4" />
  </>,
);

export const DollarIcon = () => svg(
  <>
    <path d="M16.5 7.5A3.5 3.5 0 0 0 13 5h-2a3 3 0 0 0 0 6h2a3 3 0 0 1 0 6h-2a3.5 3.5 0 0 1-3.5-2.5" />
    <path d="M12 2.5v19" />
  </>,
);

export const BitcoinIcon = () => svg(
  <>
    <path d="M7 5.5h6.2a3.25 3.25 0 0 1 0 6.5H7z" />
    <path d="M7 12h6.8a3.25 3.25 0 0 1 0 6.5H7z" />
    <path d="M7 5.5v13M10 3v2.5M10 18.5V21M14 3v2.5M14 18.5V21" />
  </>,
);

export const MenuIcon = () => svg(
  <>
    <line x1="4" y1="7" x2="20" y2="7" />
    <line x1="4" y1="12" x2="20" y2="12" />
    <line x1="4" y1="17" x2="20" y2="17" />
  </>,
);

export const CopyIcon = () => svg(
  <>
    <rect x="9" y="9" width="11" height="11" rx="2" />
    <path d="M5 15V6a2 2 0 0 1 2-2h8" />
  </>,
);

export const CheckIcon = () => svg(<polyline points="4,13 9,18 20,6" />);

export const RefreshIcon = () => svg(
  <>
    <path d="M20 12a8 8 0 1 1-2.6-5.9" />
    <polyline points="20,4 20,10 14,10" />
  </>,
);

export const ThemeIcon = ({ mode }: { mode: "system" | "light" | "dark" }) => {
  if (mode === "light") {
    return svg(<><circle cx="12" cy="12" r="4.2" /><path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4" /></>);
  }
  if (mode === "dark") return svg(<path d="M20 14.5A8.2 8.2 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z" />);
  return svg(<><circle cx="12" cy="12" r="8.5" /><path d="M12 3.5v17" /><path d="M12 20.5a8.5 8.5 0 0 0 0-17Z" fill="currentColor" stroke="none" /></>);
};

export const SwapIcon = () => svg(
  <><path d="M8 3.5v17M8 3.5 4.5 7M8 3.5 11.5 7" /><path d="M16 20.5v-17M16 20.5 12.5 17M16 20.5 19.5 17" /></>,
);
