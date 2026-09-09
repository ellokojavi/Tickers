import { useEffect, useRef, useState } from "preact/hooks";
import type { ComponentChildren, JSX } from "preact";
import * as Fmt from "../core/format.ts";
import type { IsoDate } from "../domain/dates.ts";
import type { Money } from "../domain/money.ts";

export const Card = ({ children }: { children: ComponentChildren }) => (
  <section class="card">{children}</section>
);

export const SectionTitle = ({ children }: { children: ComponentChildren }) => (
  <h2 class="section-title">{children}</h2>
);

export const KeyValue = (
  { label, value, strong = false, tone }: {
    label: string; value: string; strong?: boolean; tone?: "positive" | "negative";
  },
) => (
  <div class={`kv${strong ? " strong" : ""}`}>
    <span>{label}</span>
    <span class={tone ?? ""}>{value}</span>
  </div>
);

export const Pill = (
  { children, official = false, onClick }: {
    children: ComponentChildren; official?: boolean; onClick?: () => void;
  },
) => (
  <button
    type="button"
    class={`pill${official ? " official" : ""}`}
    onClick={onClick}
    style={onClick === undefined ? { cursor: "default" } : undefined}
  >
    {children}
  </button>
);

export const Chips = <T extends string>(
  { options, selected, onSelect, label }: {
    options: readonly { key: T; label: string }[];
    selected: T;
    onSelect: (key: T) => void;
    label: string;
  },
) => (
  <div class="chips" role="group" aria-label={label}>
    {options.map((o) => (
      <button
        key={o.key}
        type="button"
        class="chip"
        aria-pressed={o.key === selected}
        onClick={() => onSelect(o.key)}
      >
        {o.label}
      </button>
    ))}
  </div>
);

/**
 * A numeric field that shows Chilean thousands separators.
 *
 * Grouping is applied when the field loses focus rather than on every
 * keystroke. Reformatting mid-typing means moving the caret by hand, and
 * getting that subtly wrong is worse than showing raw digits for the few
 * seconds someone is typing into the box.
 */
export const NumberField = (
  { label, value, onChange, suffix, support, decimals = true, error }: {
    label: string;
    value: string;
    onChange: (raw: string) => void;
    suffix?: string;
    support?: string;
    decimals?: boolean;
    error?: boolean;
  },
) => {
  const [focused, setFocused] = useState(false);
  const shown = focused || value === "" ? value : groupForDisplay(value, decimals);

  return (
    <label class="field">
      <span>{label}</span>
      <div class="input-wrap" style={error === true ? { borderColor: "var(--error)" } : undefined}>
        <input
          type="text"
          inputMode={decimals ? "decimal" : "numeric"}
          value={shown}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onInput={(e) => onChange(sanitize((e.target as HTMLInputElement).value, decimals))}
        />
        {suffix !== undefined && <span class="suffix">{suffix}</span>}
      </div>
      {support !== undefined && <span class="support">{support}</span>}
    </label>
  );
};

/**
 * Keeps only what a Chilean number can contain. A typed "." or "," is always
 * the decimal separator: grouping is supplied by the display, never typed, and
 * a phone's numeric keypad offers whichever of the two its locale prefers.
 */
export const sanitize = (input: string, decimals: boolean): string => {
  const negative = input.trimStart().startsWith("-");
  let out = "";
  let seenDecimal = false;
  for (const c of input) {
    if (c >= "0" && c <= "9") out += c;
    else if (decimals && (c === "," || c === ".") && !seenDecimal && out.length > 0) {
      out += ",";
      seenDecimal = true;
    }
  }
  return negative ? `-${out}` : out;
};

export const groupForDisplay = (raw: string, decimals: boolean): string => {
  const negative = raw.startsWith("-");
  const body = negative ? raw.slice(1) : raw;
  const [int = "", dec] = decimals ? body.split(",") : [body, undefined];
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const out = dec === undefined ? grouped : `${grouped},${dec}`;
  return negative ? `-${out}` : out;
};

/**
 * A bounded date field. The native control enforces min and max, so a day the
 * app has no value for cannot be picked at all, and the long-form date is
 * printed underneath so the reading is unambiguous whatever order the
 * browser's own locale uses.
 */
export const DateField = (
  { label, value, onChange, min, max, quick }: {
    label: string;
    value: IsoDate;
    onChange: (d: IsoDate) => void;
    min?: IsoDate;
    max?: IsoDate;
    quick?: { label: string; onClick: () => void };
  },
) => (
  <label class="field">
    <span>{label}</span>
    <div class="input-wrap">
      <input
        type="date"
        value={value}
        min={min}
        max={max}
        onInput={(e) => {
          const next = (e.target as HTMLInputElement).value;
          if (next !== "") onChange(next);
        }}
      />
      {quick !== undefined && (
        <button type="button" class="btn text" onClick={quick.onClick}>{quick.label}</button>
      )}
    </div>
    <span class="support">{Fmt.longDate(value)}</span>
  </label>
);

/**
 * The series as a filled line, drawn by hand.
 *
 * Not a charting library: this needs one line, a gradient fill and a scrub
 * cursor, and a dependency for that would cost more than it saves. Pointer
 * tracking is horizontal only, so a finger that starts on the chart can still
 * scroll the page.
 */
export const Sparkline = (
  { values, height = 200, selected, onScrub }: {
    // Only the values are drawn; x is the index. Widened from UfValue so the
    // bitcoin chart, whose points are timestamps rather than calendar days,
    // can use the same one.
    values: readonly { readonly value: Money }[];
    height?: number;
    selected: number | null;
    onScrub: (index: number | null) => void;
  },
) => {
  const ref = useRef<HTMLCanvasElement>(null);

  /**
   * A canvas has to be told its pixel size, and CSS decides that size. On a
   * phone the width never changed, so drawing once was enough. It changes here
   * whenever the window is resized or the layout crosses into its two-column
   * form, and a canvas drawn for another width is a chart of the wrong shape.
   */
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const canvas = ref.current;
    if (canvas === null) return;
    const observer = new ResizeObserver(() => setWidth(canvas.clientWidth));
    observer.observe(canvas);
    setWidth(canvas.clientWidth);
    return () => observer.disconnect();
  }, [values.length]);

  useEffect(() => {
    const canvas = ref.current;
    if (canvas === null || values.length < 2) return;
    const css = getComputedStyle(document.documentElement);
    const line = css.getPropertyValue("--primary").trim();
    const grid = css.getPropertyValue("--outline-variant").trim();

    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth;
    if (w === 0) return;
    canvas.width = w * dpr;
    canvas.height = height * dpr;
    const ctx = canvas.getContext("2d");
    if (ctx === null) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, height);

    const nums = values.map((v) => Number(v.value.toString()));
    const min = Math.min(...nums);
    const max = Math.max(...nums);
    const span = max - min || 1;
    const padV = height * 0.1;
    const xAt = (i: number) => (w * i) / (values.length - 1);
    const yAt = (v: number) => height - padV - ((v - min) / span) * (height - 2 * padV);

    ctx.strokeStyle = grid;
    ctx.lineWidth = 1;
    for (const y of [padV, height - padV]) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
    }

    ctx.beginPath();
    ctx.moveTo(xAt(0), yAt(nums[0]!));
    for (let i = 1; i < nums.length; i++) ctx.lineTo(xAt(i), yAt(nums[i]!));

    const fill = ctx.createLinearGradient(0, 0, 0, height);
    fill.addColorStop(0, hexWithAlpha(line, 0.18));
    fill.addColorStop(1, hexWithAlpha(line, 0));
    ctx.save();
    ctx.lineTo(xAt(nums.length - 1), height);
    ctx.lineTo(xAt(0), height);
    ctx.closePath();
    ctx.fillStyle = fill;
    ctx.fill();
    ctx.restore();

    ctx.beginPath();
    ctx.moveTo(xAt(0), yAt(nums[0]!));
    for (let i = 1; i < nums.length; i++) ctx.lineTo(xAt(i), yAt(nums[i]!));
    ctx.strokeStyle = line;
    ctx.lineWidth = 2;
    ctx.stroke();

    if (selected !== null && selected >= 0 && selected < nums.length) {
      const x = xAt(selected);
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, height);
      ctx.strokeStyle = grid; ctx.lineWidth = 1.5; ctx.stroke();
      ctx.beginPath(); ctx.arc(x, yAt(nums[selected]!), 4, 0, Math.PI * 2);
      ctx.fillStyle = line; ctx.fill();
    }
  }, [values, height, selected, width]);

  const indexFrom = (clientX: number): number => {
    const canvas = ref.current;
    if (canvas === null) return 0;
    const rect = canvas.getBoundingClientRect();
    const ratio = (clientX - rect.left) / rect.width;
    return Math.min(values.length - 1, Math.max(0, Math.round(ratio * (values.length - 1))));
  };

  if (values.length < 2) return <div style={{ height }} />;

  return (
    <canvas
      ref={ref}
      class="chart"
      style={{ height }}
      role="img"
      aria-label="Evolución del valor de la UF"
      onPointerDown={(e: JSX.TargetedPointerEvent<HTMLCanvasElement>) => onScrub(indexFrom(e.clientX))}
      onPointerMove={(e: JSX.TargetedPointerEvent<HTMLCanvasElement>) => {
        if (e.buttons > 0) onScrub(indexFrom(e.clientX));
      }}
      onPointerUp={() => onScrub(null)}
      onPointerLeave={() => onScrub(null)}
    />
  );
};

const hexWithAlpha = (color: string, alpha: number): string => {
  const hex = color.replace("#", "");
  if (hex.length !== 6) return color;
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

/** A full-screen dialog, used for the payment table and the about page. */
export const Sheet = (
  { title, subtitle, onClose, actions, children }: {
    title: string;
    subtitle?: string;
    onClose: () => void;
    actions?: ComponentChildren;
    children: ComponentChildren;
  },
) => {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
    return () => ref.current?.close();
  }, []);

  return (
    <dialog class="sheet" ref={ref} onCancel={onClose} onClose={onClose}>
      <div class="sheet-bar">
        <button type="button" class="btn icon" aria-label="Cerrar" onClick={onClose}>✕</button>
        <div style={{ flex: 1 }}>
          <strong>{title}</strong>
          {subtitle !== undefined && <div class="muted">{subtitle}</div>}
        </div>
        {actions}
      </div>
      <div class="sheet-body">{children}</div>
    </dialog>
  );
};
