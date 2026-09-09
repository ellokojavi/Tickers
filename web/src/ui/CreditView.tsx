import { useMemo, useState } from "preact/hooks";
import * as Fmt from "../core/format.ts";
import { money, type Money } from "../domain/money.ts";
import { plusMonths, today as todayIso, type IsoDate } from "../domain/dates.ts";
import { financedPct, PrepaymentMode, RateConvention, type MortgageInput, type MortgageResult, type Prepayment, type PrepaymentModeKey, type RateConventionKey, type Simulation } from "../domain/models.ts";
import { simulate } from "../domain/mortgageEngine.ts";
import { ufToClp } from "../domain/ufEngine.ts";
import {
  createSimulation, deleteSimulation, duplicateSimulation, listSimulations, updateSimulation,
} from "../data/store.ts";
import type { UfData } from "../data/useUfData.ts";
import { Card, Chips, DateField, KeyValue, NumberField, SectionTitle, Sheet } from "./components.tsx";

interface Form {
  name: string; notes: string;
  propertyValueUf: string; downPaymentUf: string; annualRatePct: string; termYears: string;
  rateConvention: RateConventionKey;
  lifeInsuranceMonthlyPct: string; fireInsuranceMonthlyUf: string;
  originationFeeUf: string; stampTaxPct: string; otherUpfrontCostsUf: string;
  firstPaymentDate: IsoDate;
  prepayments: Prepayment[];
}

const emptyForm = (): Form => ({
  name: "Mi simulación", notes: "",
  propertyValueUf: "5000", downPaymentUf: "1000", annualRatePct: "4,5", termYears: "25",
  rateConvention: "NOMINAL_DIVIDED",
  lifeInsuranceMonthlyPct: "0,03", fireInsuranceMonthlyUf: "0,4",
  originationFeeUf: "0", stampTaxPct: "0,8", otherUpfrontCostsUf: "30",
  firstPaymentDate: plusMonths(todayIso(), 1),
  prepayments: [],
});

const formOf = (s: Simulation): Form => ({
  name: s.name, notes: s.notes,
  propertyValueUf: s.input.propertyValueUf.toString().replace(".", ","),
  downPaymentUf: s.input.downPaymentUf.toString().replace(".", ","),
  annualRatePct: s.input.annualRatePct.toString().replace(".", ","),
  termYears: String(s.input.termYears),
  rateConvention: s.input.rateConvention,
  lifeInsuranceMonthlyPct: s.input.lifeInsuranceMonthlyPct.toString().replace(".", ","),
  fireInsuranceMonthlyUf: s.input.fireInsuranceMonthlyUf.toString().replace(".", ","),
  originationFeeUf: s.input.originationFeeUf.toString().replace(".", ","),
  stampTaxPct: s.input.stampTaxPct.toString().replace(".", ","),
  otherUpfrontCostsUf: s.input.otherUpfrontCostsUf.toString().replace(".", ","),
  firstPaymentDate: s.input.firstPaymentDate,
  prepayments: [...s.input.prepayments],
});

const numberOr = (text: string, fallback: number): number => Fmt.parseNumber(text) ?? fallback;

const buildInput = (f: Form): { input: MortgageInput | null; error: string | null } => {
  const property = Fmt.parseNumber(f.propertyValueUf);
  const down = Fmt.parseNumber(f.downPaymentUf) ?? 0;
  const rate = Fmt.parseNumber(f.annualRatePct);
  const years = Fmt.parseNumber(f.termYears);

  if (property === null || property <= 0) return { input: null, error: "Ingresa el valor de la propiedad." };
  if (down < 0) return { input: null, error: "El pie no puede ser negativo." };
  if (down >= property) return { input: null, error: "El pie no puede ser igual o mayor que la propiedad." };
  if (rate === null || rate < 0) return { input: null, error: "Ingresa una tasa válida." };
  if (years === null || years < 1 || years > 40) return { input: null, error: "El plazo debe estar entre 1 y 40 años." };

  return {
    input: {
      propertyValueUf: money(String(property)),
      downPaymentUf: money(String(down)),
      annualRatePct: money(String(rate)),
      termYears: Math.trunc(years),
      rateConvention: f.rateConvention,
      lifeInsuranceMonthlyPct: money(String(numberOr(f.lifeInsuranceMonthlyPct, 0))),
      fireInsuranceMonthlyUf: money(String(numberOr(f.fireInsuranceMonthlyUf, 0))),
      originationFeeUf: money(String(numberOr(f.originationFeeUf, 0))),
      stampTaxPct: money(String(numberOr(f.stampTaxPct, 0))),
      otherUpfrontCostsUf: money(String(numberOr(f.otherUpfrontCostsUf, 0))),
      firstPaymentDate: f.firstPaymentDate,
      prepayments: f.prepayments,
    },
    error: null,
  };
};

export const CreditView = ({ data }: { data: UfData }) => {
  const [editing, setEditing] = useState<{ id: string | null } | null>(null);
  const [version, setVersion] = useState(0);
  const rows = useMemo(() => listSimulations(), [version, editing]);
  const rate = data.series.length === 0 ? null
    : data.series.filter((v) => v.date <= todayIso()).at(-1)?.value ?? null;

  if (editing !== null) {
    return (
      <Editor
        id={editing.id}
        ufValue={rate}
        onDone={() => { setEditing(null); setVersion((v) => v + 1); }}
      />
    );
  }

  return (
    <>
      {rows.length === 0 ? (
        <div class="empty">
          <div style={{ fontSize: 56, lineHeight: 1 }}>🏛</div>
          <p><strong>Sin simulaciones</strong></p>
          <p class="muted">Crea una para modelar un crédito UF + tasa y guardar la tabla de pagos.</p>
        </div>
      ) : rows.map((s) => {
        const r = simulate(s.input);
        return (
          <Card key={s.id}>
            <div class="row">
              <button
                type="button"
                class="btn text"
                style={{ padding: 0, fontSize: 16, fontWeight: 600, color: "inherit" }}
                onClick={() => setEditing({ id: s.id })}
              >{s.name}</button>
              <span>
                <button type="button" class="btn text" onClick={() => {
                  duplicateSimulation(s.id, `${s.name} (copia)`); setVersion((v) => v + 1);
                }}>Duplicar</button>
                <button type="button" class="btn text" onClick={() => {
                  if (confirm(`¿Eliminar "${s.name}"?`)) { deleteSimulation(s.id); setVersion((v) => v + 1); }
                }}>Eliminar</button>
              </span>
            </div>
            <p class="muted" style={{ margin: "2px 0 10px" }}>
              UF + {Fmt.pct(s.input.annualRatePct)} · {s.input.termYears} años
            </p>
            <p class="headline">{Fmt.uf(r.firstTotalPaymentUf)}</p>
            {rate !== null && (
              <p class="muted">≈ {Fmt.clp(ufToClp(r.firstTotalPaymentUf, rate))} al mes</p>
            )}
            <KeyValue label="Monto del crédito" value={Fmt.uf(r.loanAmountUf)} />
            {r.caePct !== null && <KeyValue label="CAE" value={Fmt.pct(r.caePct)} />}
          </Card>
        );
      })}
      <button type="button" class="fab" onClick={() => setEditing({ id: null })}>+ Nueva</button>
    </>
  );
};

const Editor = (
  { id, ufValue, onDone }: { id: string | null; ufValue: Money | null; onDone: () => void },
) => {
  const existing = useMemo(() => (id === null ? null : listSimulations().find((s) => s.id === id) ?? null), [id]);
  const [form, setForm] = useState<Form>(() => (existing === null ? emptyForm() : formOf(existing)));
  const [showTable, setShowTable] = useState(false);
  const isNew = existing === null;

  const { input, error } = useMemo(() => buildInput(form), [form]);
  const result = useMemo(() => (input === null ? null : simulate(input)), [input]);
  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm({ ...form, [key]: value });

  const save = () => {
    if (input === null) return;
    if (existing === null) createSimulation(form.name || "Simulación", form.notes, input);
    else updateSimulation({ ...existing, name: form.name || "Simulación", notes: form.notes, input });
    onDone();
  };

  const resultCard = result === null ? null : (
    <Card>
      <SectionTitle>Resultado</SectionTitle>
      <p class="display">{Fmt.uf(result.firstTotalPaymentUf)}</p>
      {ufValue !== null && (
        <p class="muted">≈ {Fmt.clp(ufToClp(result.firstTotalPaymentUf, ufValue))} la primera cuota</p>
      )}
      <hr />
      <KeyValue label="Monto del crédito" value={Fmt.uf(result.loanAmountUf)} />
      <KeyValue label="Dividendo (capital + interés)" value={Fmt.uf(result.basePaymentUf)} />
      <KeyValue label="Total intereses" value={Fmt.uf(result.totalInterestUf)} />
      <KeyValue label="Total seguros" value={Fmt.uf(result.totalLifeInsuranceUf.plus(result.totalFireInsuranceUf))} />
      <KeyValue label="Gastos iniciales" value={Fmt.uf(result.upfrontCostsUf)} />
      {result.totalPrepaymentsUf.cmp(0) > 0 && (
        <>
          <KeyValue label="Prepagos" value={Fmt.uf(result.totalPrepaymentsUf)} />
          <KeyValue label="Cuotas ahorradas" value={Fmt.integer(result.monthsSaved)} />
        </>
      )}
      <KeyValue label="Cuotas" value={Fmt.integer(result.effectiveTermMonths)} />
      <hr />
      <KeyValue label="Costo total" value={Fmt.uf(result.totalCostUf)} strong />
      {result.caePct !== null && <KeyValue label="CAE" value={Fmt.pct(result.caePct)} strong />}
      {ufValue !== null && (
        <p class="tiny" style={{ marginTop: 10 }}>
          Los montos en pesos usan la UF de hoy ({Fmt.clpExact(ufValue)}) y cambiarán con la
          inflación. La deuda en UF no cambia.
        </p>
      )}
    </Card>
  );

  return (
    <>
      <div class="row" style={{ minHeight: 56 }}>
        <button type="button" class="btn text" onClick={onDone}>← Volver</button>
        <strong>{isNew ? "Nueva simulación" : "Editar simulación"}</strong>
        <span />
      </div>

      {/* Opening something that already exists is a consultation, not an act of
          creation, so a saved simulation leads with its result. */}
      {!isNew && resultCard}
      {!isNew && result !== null && (
        <button type="button" class="btn tonal" style={{ width: "100%", marginBottom: 12 }}
          onClick={() => setShowTable(true)}>Ver tabla de pagos</button>
      )}

      <Card>
        <label class="field">
          <span>Nombre</span>
          <div class="input-wrap">
            <input value={form.name} onInput={(e) => set("name", (e.target as HTMLInputElement).value)} />
          </div>
        </label>
        <label class="field">
          <span>Notas</span>
          <div class="input-wrap">
            <input value={form.notes} onInput={(e) => set("notes", (e.target as HTMLInputElement).value)} />
          </div>
        </label>
      </Card>

      <Card>
        <SectionTitle>Crédito</SectionTitle>
        <NumberField
          label="Valor de la propiedad" suffix="UF" value={form.propertyValueUf}
          onChange={(v) => set("propertyValueUf", v)}
          support={ufValue !== null && Fmt.parseNumber(form.propertyValueUf) !== null
            ? `≈ ${Fmt.clp(ufToClp(money(String(Fmt.parseNumber(form.propertyValueUf))), ufValue))}`
            : undefined}
        />
        <NumberField
          label="Pie" suffix="UF" value={form.downPaymentUf}
          onChange={(v) => set("downPaymentUf", v)}
          support={input === null ? undefined
            : `El banco financia ${Fmt.pct(financedPct(input))} de la propiedad`}
        />
        <NumberField
          label="Tasa anual sobre la UF" suffix="%" value={form.annualRatePct}
          onChange={(v) => set("annualRatePct", v)}
          support={`El crédito queda en UF + ${form.annualRatePct}%`}
        />
        <NumberField
          label="Plazo" suffix="años" decimals={false} value={form.termYears}
          onChange={(v) => set("termYears", v)}
        />
        <DateField
          label="Vencimiento de la primera cuota"
          value={form.firstPaymentDate}
          onChange={(d) => set("firstPaymentDate", d)}
        />
        <p class="tiny" style={{ marginTop: -6, marginBottom: 12 }}>
          {Number(form.firstPaymentDate.slice(8)) > 28
            ? `Las cuotas vencen el ${Number(form.firstPaymentDate.slice(8))} de cada mes; en los meses más cortos se corren al último día.`
            : `Las cuotas siguientes vencen el ${Number(form.firstPaymentDate.slice(8))} de cada mes.`}
        </p>
        <span class="section-title">Conversión de la tasa</span>
        <Chips
          label="Conversión de la tasa"
          options={[
            { key: "NOMINAL_DIVIDED" as RateConventionKey, label: "Anual / 12" },
            { key: "EFFECTIVE_EQUIVALENT" as RateConventionKey, label: "Efectiva" },
          ]}
          selected={form.rateConvention}
          onSelect={(k) => set("rateConvention", k)}
        />
        <p class="tiny" style={{ marginTop: 8 }}>
          Los bancos chilenos no usan todos la misma conversión ({RateConvention[form.rateConvention]}).
          Si tu cotización no calza, prueba la otra opción.
        </p>
      </Card>

      <Card>
        <SectionTitle>Seguros y costos</SectionTitle>
        <NumberField label="Desgravamen mensual sobre el saldo" suffix="%"
          value={form.lifeInsuranceMonthlyPct} onChange={(v) => set("lifeInsuranceMonthlyPct", v)} />
        <NumberField label="Incendio y sismo (mensual)" suffix="UF"
          value={form.fireInsuranceMonthlyUf} onChange={(v) => set("fireInsuranceMonthlyUf", v)} />
        <NumberField label="Impuesto de timbres" suffix="%"
          value={form.stampTaxPct} onChange={(v) => set("stampTaxPct", v)} />
        <NumberField label="Comisión" suffix="UF"
          value={form.originationFeeUf} onChange={(v) => set("originationFeeUf", v)} />
        <NumberField label="Notaría, tasación, conservador" suffix="UF"
          value={form.otherUpfrontCostsUf} onChange={(v) => set("otherUpfrontCostsUf", v)} />
      </Card>

      <Card>
        <div class="row">
          <SectionTitle>Prepagos</SectionTitle>
          <button type="button" class="btn text" onClick={() => {
            const monthText = prompt("¿En qué número de cuota?", "12");
            if (monthText === null) return;
            const amountText = prompt("¿Cuánto abonas, en UF?", "100");
            if (amountText === null) return;
            const m = Fmt.parseNumber(monthText);
            const a = Fmt.parseNumber(amountText);
            if (m === null || a === null || m < 1 || a <= 0) return;
            const mode: PrepaymentModeKey =
              confirm("¿Acortar el plazo? Cancela para bajar la cuota.") ? "REDUCE_TERM" : "REDUCE_PAYMENT";
            set("prepayments", [...form.prepayments, { monthNumber: Math.trunc(m), amountUf: money(String(a)), mode }]);
          }}>Agregar</button>
        </div>
        {form.prepayments.length === 0
          ? <p class="muted">Sin abonos a capital.</p>
          : form.prepayments.map((p, index) => (
            <div class="row" key={`${p.monthNumber}-${index}`}>
              <span>
                Cuota {Fmt.integer(p.monthNumber)} · {Fmt.uf(p.amountUf)}
                <br /><span class="tiny">{PrepaymentMode[p.mode]}</span>
              </span>
              <button type="button" class="btn text" onClick={() =>
                set("prepayments", form.prepayments.filter((_, k) => k !== index))
              }>Quitar</button>
            </div>
          ))}
      </Card>

      {error !== null && <p class="banner" style={{ color: "var(--error)" }}>{error}</p>}

      {isNew && resultCard}
      {isNew && result !== null ? (
        <div class="btn-row">
          <button type="button" class="btn tonal" onClick={() => setShowTable(true)}>Ver tabla de pagos</button>
          <button type="button" class="btn filled" onClick={save}>Guardar</button>
        </div>
      ) : result !== null && (
        <div class="btn-row">
          <button type="button" class="btn filled" onClick={save}>Actualizar</button>
        </div>
      )}
      {!isNew && (
        <button type="button" class="btn text" style={{ width: "100%", marginTop: 12, color: "var(--error)" }}
          onClick={() => {
            if (existing !== null && confirm(`¿Eliminar "${existing.name}"?`)) {
              deleteSimulation(existing.id); onDone();
            }
          }}>Eliminar simulación</button>
      )}

      {showTable && result !== null && (
        <ScheduleSheet
          title={form.name} result={result} ufValue={ufValue}
          onClose={() => setShowTable(false)}
        />
      )}
    </>
  );
};

const ScheduleSheet = (
  { title, result, ufValue, onClose }: {
    title: string; result: MortgageResult; ufValue: Money | null; onClose: () => void;
  },
) => (
  <Sheet
    title="Tabla de pagos"
    subtitle={`${Fmt.integer(result.schedule.length)} cuotas · valores en UF`}
    onClose={onClose}
    actions={
      <button type="button" class="btn text" onClick={() => downloadCsv(title, result, ufValue)}>
        CSV
      </button>
    }
  >
    <div class="table-scroll">
      <table class="schedule">
        <thead>
          <tr>
            <th>N°</th><th>Fecha</th><th>Saldo inicial</th><th>Interés</th><th>Amortiza</th>
            <th>Dividendo</th><th>Desgrav.</th><th>Incendio</th><th>Prepago</th>
            <th>Total mes</th><th>Saldo final</th>
          </tr>
        </thead>
        <tbody>
          {result.schedule.map((row) => (
            <tr key={row.number}>
              <td>{Fmt.integer(row.number)}</td>
              <td>{Fmt.shortDate(row.date)}</td>
              <td>{Fmt.uf(row.openingBalanceUf).replace(" UF", "")}</td>
              <td>{Fmt.uf(row.interestUf).replace(" UF", "")}</td>
              <td>{Fmt.uf(row.principalUf).replace(" UF", "")}</td>
              <td>{Fmt.uf(row.paymentUf).replace(" UF", "")}</td>
              <td>{Fmt.uf(row.lifeInsuranceUf).replace(" UF", "")}</td>
              <td>{Fmt.uf(row.fireInsuranceUf).replace(" UF", "")}</td>
              <td>{row.prepaymentUf.cmp(0) > 0 ? Fmt.uf(row.prepaymentUf).replace(" UF", "") : "—"}</td>
              <td>{Fmt.uf(row.totalOutflowUf).replace(" UF", "")}</td>
              <td>{Fmt.uf(row.closingBalanceUf).replace(" UF", "")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  </Sheet>
);

/**
 * ";" as the separator and "," as the decimal, which is what a spreadsheet
 * expects under a Chilean locale. A comma separator would split every amount.
 */
const downloadCsv = (name: string, result: MortgageResult, ufValue: Money | null): void => {
  const dec = (v: Money): string => v.toString().replace(".", ",");
  const head = [
    `Simulacion;${name}`,
    `Monto del credito;${dec(result.loanAmountUf)};UF`,
    `Tasa anual;${dec(result.input.annualRatePct)};%`,
    `Convencion;${RateConvention[result.input.rateConvention]}`,
    `Plazo;${result.input.termYears};anios`,
    `Primer vencimiento;${Fmt.shortDate(result.input.firstPaymentDate)}`,
    result.caePct === null ? "" : `CAE;${dec(result.caePct)};%`,
    ufValue === null ? "" : `Valor UF usado;${dec(ufValue)};CLP`,
    "",
    ["N", "Fecha", "Saldo inicial UF", "Interes UF", "Amortizacion UF", "Dividendo UF",
      "Desgravamen UF", "Incendio UF", "Prepago UF", "Total mes UF", "Saldo final UF"].join(";"),
  ].filter((l) => l !== "");

  const body = result.schedule.map((r) => [
    r.number, Fmt.shortDate(r.date), dec(r.openingBalanceUf), dec(r.interestUf),
    dec(r.principalUf), dec(r.paymentUf), dec(r.lifeInsuranceUf), dec(r.fireInsuranceUf),
    dec(r.prepaymentUf), dec(r.totalOutflowUf), dec(r.closingBalanceUf),
  ].join(";"));

  const blob = new Blob([[...head, ...body].join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${name.replace(/[^A-Za-z0-9._-]/g, "_") || "simulacion"}.csv`;
  link.click();
  URL.revokeObjectURL(url);
};
