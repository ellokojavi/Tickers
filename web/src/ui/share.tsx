import { useState } from "preact/hooks";
import { CheckIcon, CopyIcon, ShareIcon } from "./icons.tsx";

export type ShareOutcome = "shared" | "copied" | "cancelled" | "failed";

/**
 * Safari proper, with every browser that merely embeds WebKit ruled out.
 */
export const isSafari = (): boolean =>
  /^((?!chrome|chromium|android|crios|fxios|edg|opr|samsungbrowser).)*safari/i.test(
    navigator.userAgent,
  );

/**
 * Whether this is a phone or a tablet, as opposed to a laptop or desktop.
 *
 * Android and iPhone say so in the user agent. iPadOS reports itself as a
 * Mac, so it is recognised as Safari with a touch screen; requiring Safari is
 * what keeps a Mac browser in touch-emulation mode from counting. Anything
 * else is handheld when its primary pointer is a finger, which covers the
 * remaining tablets and leaves out touch-screen laptops driven by a trackpad.
 */
export const isHandheld = (): boolean => {
  if (/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) return true;
  if (isSafari() && navigator.maxTouchPoints > 1) return true;
  return window.matchMedia("(pointer: coarse)").matches;
};

/**
 * Whether text should go out through the share sheet rather than the
 * clipboard.
 *
 * The rule is by device, not by what the browser happens to implement. On a
 * phone or a tablet the natural next step is another app, so the share sheet.
 * On a laptop or a desktop the natural next step is pasting somewhere, so the
 * clipboard, even in the browsers that do offer a share panel there. The
 * button says which one it is about to do rather than promising to share and
 * quietly copying instead.
 */
export const canShare = (): boolean => isHandheld() && typeof navigator.share === "function";

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
