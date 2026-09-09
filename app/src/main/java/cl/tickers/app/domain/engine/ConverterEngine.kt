package cl.tickers.app.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The three-field converters.
 *
 * Each screen has one amount and two derived from it, and the user may type in
 * any of the three. The trap is deriving one display value from another display
 * value: those have already been rounded for the eye, and rounding twice makes
 * the same quantity come out differently depending on which box was touched.
 * One bitcoin was showing 72.476.915 pesos on load and 72.476.920 after an
 * edit, purely from that.
 *
 * So every field is derived once, at full precision, from a single anchor. Only
 * the last step rounds, and the field the user is typing in is never rewritten
 * under their fingers.
 *
 * The TypeScript twin is web/src/domain/converter.ts, pinned to the same golden
 * vectors. See shared/PARITY.md.
 */
object ConverterEngine {

    private const val UF_SCALE = 4

    /** Room to divide before the single rounding at the end. */
    private const val WORKING_SCALE = 30

    enum class UfField { UF, CLP, USD }
    enum class BtcField { BTC, USD, CLP }

    data class UfConversion(val uf: BigDecimal, val clp: BigDecimal, val usd: BigDecimal?)

    /**
     * UF is the anchor. [usdClp] is the observed dollar, and may be absent when
     * the app has not managed to fetch it, in which case dollars are not offered.
     */
    fun ufConvert(
        field: UfField,
        amount: BigDecimal,
        ufRate: BigDecimal,
        usdClp: BigDecimal?,
    ): UfConversion? {
        if (ufRate.signum() <= 0) return null
        val dollar = usdClp?.takeIf { it.signum() > 0 }
        val uf = when (field) {
            UfField.UF -> amount
            UfField.CLP -> amount.divide(ufRate, WORKING_SCALE, RoundingMode.HALF_UP)
            UfField.USD -> dollar?.let {
                amount.multiply(it).divide(ufRate, WORKING_SCALE, RoundingMode.HALF_UP)
            }
        } ?: return null

        val clpExact = uf.multiply(ufRate)
        return UfConversion(
            uf = uf.setScale(UF_SCALE, RoundingMode.HALF_UP),
            clp = clpExact.setScale(0, RoundingMode.HALF_UP),
            usd = dollar?.let {
                clpExact.divide(it, FxEngine.USD_SCALE, RoundingMode.HALF_UP)
            },
        )
    }

    data class BtcConversion(val btc: BigDecimal, val usd: BigDecimal, val clp: BigDecimal)

    /** Bitcoin is the anchor, and the chain runs bitcoin to dollars to pesos. */
    fun btcConvert(
        field: BtcField,
        amount: BigDecimal,
        btcUsd: BigDecimal,
        usdClp: BigDecimal,
    ): BtcConversion? {
        if (btcUsd.signum() <= 0 || usdClp.signum() <= 0) return null
        val btc = when (field) {
            BtcField.BTC -> amount
            BtcField.USD -> amount.divide(btcUsd, WORKING_SCALE, RoundingMode.HALF_UP)
            BtcField.CLP -> amount
                .divide(usdClp, WORKING_SCALE, RoundingMode.HALF_UP)
                .divide(btcUsd, WORKING_SCALE, RoundingMode.HALF_UP)
        }

        val usdExact = btc.multiply(btcUsd)
        return BtcConversion(
            btc = btc.setScale(BtcEngine.BTC_SCALE, RoundingMode.HALF_UP),
            usd = usdExact.setScale(FxEngine.USD_SCALE, RoundingMode.HALF_UP),
            clp = usdExact.multiply(usdClp).setScale(0, RoundingMode.HALF_UP),
        )
    }
}
