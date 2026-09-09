package cl.tickers.app.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pesos and dollars.
 *
 * The rate is always the Banco Central's dólar observado, which is published
 * once a business day. Nothing here knows that; it is the screens' job to say
 * which day's rate they used, because a conversion carrying no date invites
 * being read as live.
 *
 * Pesos have no cents in Chile, so a peso amount rounds to the unit. Dollars
 * keep two.
 *
 * The TypeScript twin is web/src/domain/fx.ts. See shared/PARITY.md.
 */
object FxEngine {

    const val USD_SCALE = 2

    fun usdToClp(usd: BigDecimal, usdClp: BigDecimal): BigDecimal =
        usd.multiply(usdClp).setScale(0, RoundingMode.HALF_UP)

    fun clpToUsd(clp: BigDecimal, usdClp: BigDecimal): BigDecimal =
        if (usdClp.signum() == 0) BigDecimal.ZERO
        else clp.divide(usdClp, USD_SCALE, RoundingMode.HALF_UP)
}
