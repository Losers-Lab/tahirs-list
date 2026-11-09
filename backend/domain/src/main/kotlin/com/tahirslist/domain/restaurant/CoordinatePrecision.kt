package com.tahirslist.domain.restaurant

import kotlin.math.round
import kotlin.math.sqrt

/**
 * Decimal places a search centre is snapped to (SC-199, D-1). A snapped centre
 * is an ordinary [LatLng]; the snap only merges centres that fall in one cell.
 */
enum class CoordinatePrecision(val decimals: Int) {
    THREE_DECIMALS(3),
    FOUR_DECIMALS(4);

    fun snap(center: LatLng): LatLng = center.roundedTo(decimals)

    /**
     * Largest possible shift from a centre to its snapped value, at the equator:
     * half the diagonal of one cell (one degree is about 111 319.5 m).
     */
    val maxShiftMetres: Double
        get() {
            val cellSideMetres = METRES_PER_DEGREE / Math.pow(10.0, decimals.toDouble())
            return cellSideMetres * sqrt(2.0) / 2.0
        }

    private companion object {
        const val METRES_PER_DEGREE = 111_319.49
    }
}
