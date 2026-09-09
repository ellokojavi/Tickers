import { Card, SectionTitle, Sheet } from "./components.tsx";

/**
 * Independence notice, source attribution and the financial disclaimer.
 *
 * Reached from the footer of the main screen and from the data-source badge,
 * never pushed at the reader: nothing interrupts and there is nothing to
 * acknowledge. It exists because the app shows official figures under a
 * Chilean flag, which could otherwise read as a government service, and
 * because the CMF's terms of use require the source to be named with a link to
 * its site wherever its data is republished.
 */
export const AboutSheet = ({ onClose }: { onClose: () => void }) => (
  <Sheet title="Acerca de" onClose={onClose}>
    <Card>
      <SectionTitle>Aplicación independiente</SectionTitle>
      <p class="muted">
        UF Chile es una aplicación independiente. No está afiliada, patrocinada ni respaldada por
        la Comisión para el Mercado Financiero (CMF), el Banco Central de Chile, el Instituto
        Nacional de Estadísticas (INE), ni por ningún banco o institución financiera.
      </p>
    </Card>

    <Card>
      <SectionTitle>Fuentes de los datos</SectionTitle>
      <p class="muted">
        El valor de la UF proviene de la Comisión para el Mercado Financiero y de mindicador.cl,
        que replica los datos del Banco Central de Chile.
      </p>
      <p>
        <a href="https://www.cmfchile.cl" target="_blank" rel="noreferrer">
          Comisión para el Mercado Financiero
        </a>
      </p>
      <p><a href="https://api.cmfchile.cl" target="_blank" rel="noreferrer">API CMF Bancos</a></p>
      <p><a href="https://mindicador.cl" target="_blank" rel="noreferrer">mindicador.cl</a></p>
    </Card>

    <Card>
      <SectionTitle>Cómo se calcula</SectionTitle>
      <p class="muted">
        La serie diaria completa de la UF, desde agosto de 1977, viene incluida en la aplicación,
        por lo que todo funciona sin conexión. Los reajustes se calculan convirtiendo el monto a UF
        en la fecha inicial y de vuelta a pesos en la final.
      </p>
      <p class="muted">
        Los valores que la fuente entrega con errores evidentes se descartan y se reconstruyen desde
        su propio período de reajuste. Ningún valor se inventa ni se proyecta: si un dato no existe,
        la aplicación lo dice.
      </p>
    </Card>

    <Card>
      <SectionTitle>Aviso</SectionTitle>
      <p class="muted">
        Las cifras se entregan solo con fines informativos y no constituyen asesoría financiera ni
        de inversión.
      </p>
      <p class="muted">
        Las simulaciones de crédito son modelos, no cotizaciones. Cada banco aplica sus propias
        convenciones de tasa, comisiones y seguros. Confirma siempre las condiciones con la
        institución antes de tomar una decisión.
      </p>
    </Card>

    <Card>
      <SectionTitle>Versión</SectionTitle>
      <p class="muted">UF Chile {__APP_VERSION__} · versión web</p>
      <p>
        <a href="https://github.com/ellokojavi/UFChile" target="_blank" rel="noreferrer">
          Código fuente, app para Android y licencia MIT
        </a>
      </p>
    </Card>
  </Sheet>
);
