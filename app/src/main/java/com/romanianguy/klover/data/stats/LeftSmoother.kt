package com.romanianguy.klover.data.stats

import kotlin.math.abs

// keeps the shown time left steady: it counts down with the reading and the raw estimate only nudges it (at most STEP
// per update,
// nothing inside DEADBAND). A change of BREAK or more is real and taken at once
class LeftSmoother {
    private var shown = 0f
    private var units = 0f
    private var started = false

    fun reset() {
        started = false
    }

    fun next(unitsLeft: Float, target: Float): Float {
        if (!started || shown <= 0f || units <= 0f || unitsLeft <= 0f) {
            started = true
            shown = target
            units = unitsLeft
            return shown
        }
        val expected = shown * (unitsLeft / units)
        val ratio = if (expected > 0f) target / expected else 1f
        val change = abs(ratio - 1f)
        val applied = when {
            change < DEADBAND -> 1f
            change >= BREAK -> ratio
            else -> ratio.coerceIn(1f - STEP, 1f + STEP)
        }
        shown = expected * applied
        units = unitsLeft
        return shown
    }

    // carries the shown time on without a new estimate, for things that update many times a second like speech
    fun scaled(unitsLeft: Float, fallback: Float): Float = if (!started || units <= 0f) fallback else shown * (unitsLeft / units)

    private companion object {
        const val DEADBAND = 0.06f
        const val STEP = 0.05f
        const val BREAK = 0.35f
    }
}
