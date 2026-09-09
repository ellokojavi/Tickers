import { useState } from "preact/hooks";
import { CheckIcon, CopyIcon, ShareIcon } from "./icons.tsx";

export type ShareOutcome = "shared" | "copied" | "cancelled" | "failed";

/**
 * Whether the browser can hand text to another app.
 *
 * Phones can; most desktop browsers cannot, and there the only thing left is
 * the clipboard. That is a different action and the button says so rather than
 * promising to share and quietly copying instead.
 */
export const canShare = (): boolean => typeof navigator.share === "function";

export const shareText = async (title: string, text: string): Promise<ShareOutcome> => {
  if (canShare()) {
    try {
      await navigator.share({ title, text });
      return "shared";
    } catch (e) {
      // Closing the share sheet throws as well, and is not something to report
      // back as a failure. Anything else falls through to the clipboard.
      if (e instanceof DOMException && e.name === "AbortError") return "cancelled";
    }
  }
  try {
    await navigator.clipboard.writeText(text);
    return "copied";
  } catch {
    // No clipboard at all: a page served over plain http, or one the browser
    // will not grant it to.
    return "failed";
  }
};

/**
 * `what` completes the button's label, so it reads "Compartir el valor de la
 * UF" or "Copiar el valor de la UF al portapapeles".
 */
export const ShareButton = (
  { what, title, text }: { what: string; title: string; text: string },
) => {
  const [flash, setFlash] = useState<"copied" | "failed" | null>(null);
  const shares = canShare();

  const run = async () => {
    const outcome = await shareText(title, text);
    if (outcome !== "copied" && outcome !== "failed") return;
    setFlash(outcome);
    window.setTimeout(() => setFlash(null), 2500);
  };

  const label = flash === "copied"
    ? "Copiado al portapapeles"
    : flash === "failed"
    ? "No se pudo copiar"
    : shares
    ? `Compartir ${what}`
    : `Copiar ${what} al portapapeles`;

  return (
    <span class="share">
      {/* Announced rather than only drawn: the icon swap alone tells a screen
          reader nothing. */}
      <span class="share-flash" role="status">{flash === null ? "" : label}</span>
      <button type="button" class="btn icon" aria-label={label} title={label} onClick={() => void run()}>
        {flash === "copied" ? <CheckIcon /> : shares ? <ShareIcon /> : <CopyIcon />}
      </button>
    </span>
  );
};
