import { useEffect, useState } from "preact/hooks";
import { ShareIcon } from "./icons.tsx";
import { canShare, isHandheld, isSafari, shareText } from "./share.tsx";

/**
 * Offers to put the app on the home screen, so nobody has to retype the URL.
 *
 * Platform-aware because the platforms genuinely differ: Chrome and Edge fire
 * beforeinstallprompt and can install in one tap, while iOS Safari has no
 * programmatic install at all and the only honest thing to do is name the two
 * taps it takes. Firefox has neither, so it gets its menu described.
 *
 * On a laptop or a desktop there is no home screen to speak of, and even
 * where the browser can install the app as a window, what people actually
 * do to come back to a site is bookmark it. So there the card asks for a
 * bookmark instead, and names the shortcut for it.
 *
 * It disappears once the app is installed, and dismissing it is remembered.
 * A prompt that keeps coming back after a "no" is a nag, not a convenience.
 */

interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

// Keeps its old prefix for the reason given in data/store.ts: renaming a
// storage key does not carry the value across, it abandons it, and someone who
// has already dismissed this card should not be asked again.
const DISMISSED_KEY = "ufchile.installDismissed.v1";

const isStandalone = (): boolean =>
  window.matchMedia("(display-mode: standalone)").matches ||
  (window.navigator as { standalone?: boolean }).standalone === true;

/**
 * True where installing means going through the share menu, which is Safari on
 * iPhone and iPad.
 *
 * iPadOS reports itself as a Mac, so the usual trick is platform plus touch
 * points. That alone is not enough: any Mac browser in touch-emulation mode
 * reports MacIntel with touch points and would wrongly be told to look for a
 * Safari share button. Requiring Safari as well is what makes it correct.
 */
const usesShareMenu = (): boolean => {
  if (/iPhone|iPad|iPod/i.test(navigator.userAgent)) return true;
  return isSafari() && navigator.maxTouchPoints > 1;
};

/** Where the bookmark shortcut is ⌘ D rather than Ctrl D. */
const isMacLike = (): boolean => /Mac|iPhone|iPad|iPod/i.test(navigator.platform);

const wasDismissed = (): boolean => {
  try { return localStorage.getItem(DISMISSED_KEY) === "1"; } catch { return false; }
};

export const InstallCard = ({ compact = false }: { compact?: boolean }) => {
  const [deferred, setDeferred] = useState<InstallPromptEvent | null>(null);
  const [installed, setInstalled] = useState(isStandalone);
  const [dismissed, setDismissed] = useState(() => !compact && wasDismissed());
  const [copied, setCopied] = useState<"copied" | "failed" | null>(null);

  useEffect(() => {
    const onPrompt = (event: Event) => {
      // Held so the offer appears inside the app's own layout rather than as a
      // browser bar the user has already learned to ignore.
      event.preventDefault();
      setDeferred(event as InstallPromptEvent);
    };
    const onInstalled = () => { setInstalled(true); setDeferred(null); };

    window.addEventListener("beforeinstallprompt", onPrompt);
    window.addEventListener("appinstalled", onInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onPrompt);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);

  if (installed || dismissed) return null;

  const dismiss = () => {
    setDismissed(true);
    try { localStorage.setItem(DISMISSED_KEY, "1"); } catch { /* ignore */ }
  };

  const install = async () => {
    if (deferred === null) return;
    await deferred.prompt();
    const choice = await deferred.userChoice;
    setDeferred(null);
    if (choice.outcome === "accepted") setInstalled(true);
  };

  const shareLink = async () => {
    const url = window.location.href;
    const outcome = await shareText("Tickers", url);
    if (outcome !== "copied" && outcome !== "failed") return;
    setCopied(outcome);
    window.setTimeout(() => setCopied(null), 2500);
  };

  // On a laptop the link is copied rather than shared, and the button says
  // which one it is about to do.
  const linkLabel = copied === "copied"
    ? "Enlace copiado"
    : copied === "failed"
    ? "No se pudo copiar"
    : canShare()
    ? "Compartir enlace"
    : "Copiar enlace";

  const closeButton = !compact && (
    <button type="button" class="btn icon install-close" aria-label="Ocultar" onClick={dismiss}>
      ✕
    </button>
  );

  if (!isHandheld()) {
    return (
      <section class="card install">
        {closeButton}
        <h2 class="section-title">Guardar en favoritos</h2>
        <p class="muted">
          En un computador lo práctico es tener Tickers entre los favoritos del navegador, para
          volver sin escribir la dirección.
        </p>
        <ol class="steps">
          <li>
            Presiona <kbd>{isMacLike() ? "⌘" : "Ctrl"}</kbd> <kbd>D</kbd>, o la estrella en la barra
            de direcciones
          </li>
          <li>Elige dónde guardarlo y confirma</li>
        </ol>
        <button type="button" class="btn outlined" style={{ width: "100%" }}
          onClick={() => void shareLink()}>
          {linkLabel}
        </button>
      </section>
    );
  }

  return (
    <section class="card install">
      {closeButton}
      <h2 class="section-title">Descargar acceso directo</h2>

      {deferred !== null ? (
        <>
          <p class="muted">
            Instálala y queda como una app más, con su propio ícono y sin barra del navegador.
          </p>
          <div class="btn-row" style={{ marginTop: 12 }}>
            <button type="button" class="btn filled" onClick={() => void install()}>
              Instalar
            </button>
            <button type="button" class="btn outlined" onClick={() => void shareLink()}>
              {linkLabel}
            </button>
          </div>
        </>
      ) : usesShareMenu() ? (
        <>
          <p class="muted">
            Agrega Tickers a tu pantalla de inicio para que quede con su propio ícono, sin tener
            que escribir la dirección otra vez.
          </p>
          <ol class="steps">
            <li>
              Toca <span class="glyph"><ShareIcon /></span> Compartir, abajo en Safari
            </li>
            <li>Elige <strong>Añadir a pantalla de inicio</strong></li>
          </ol>
          <button type="button" class="btn outlined" style={{ width: "100%" }}
            onClick={() => void shareLink()}>
            {linkLabel}
          </button>
        </>
      ) : (
        <>
          <p class="muted">
            Agrega Tickers a tu pantalla de inicio para que quede con su propio ícono, sin tener
            que escribir la dirección otra vez.
          </p>
          <ol class="steps">
            <li>Abre el menú del navegador</li>
            <li>Elige <strong>Instalar app</strong> o <strong>Agregar a pantalla principal</strong></li>
          </ol>
          <button type="button" class="btn outlined" style={{ width: "100%" }}
            onClick={() => void shareLink()}>
            {linkLabel}
          </button>
        </>
      )}
    </section>
  );
};
