package com.myframe.tv.core

/**
 * Time-of-day behaviour for the picture on screen. Pure functions, so they can be unit-tested without a TV.
 * None of this touches the TV's backlight (apps are not allowed to): it only changes what MyFrame draws.
 */
object Ambience {
    /** True when [hour] falls inside the nightly "screen off" window. Handles windows that cross midnight (23 to 7). */
    fun isNight(hour: Int, startHour: Int, endHour: Int): Boolean = when {
        startHour == endHour -> false
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }

    /**
     * Extra darkening for evenings, 0 (daytime) to [MAX_EVENING_DIM]. Ramps in from 19:00 to 22:00, stays overnight,
     * and ramps out between 06:00 and 07:00, so the change is never a visible jump.
     */
    fun eveningDim(hour: Int, minute: Int): Float {
        val t = hour + minute / 60f
        val level = when {
            t >= 22f || t < 6f -> 1f
            t >= 19f -> (t - 19f) / 3f
            t < 7f -> 7f - t
            else -> 0f
        }
        return level.coerceIn(0f, 1f) * MAX_EVENING_DIM
    }

    /**
     * Burn-in protection: the whole picture moves by a pixel or two every [DRIFT_STEP_MIN] minutes along a small
     * closed loop, so no pixel shows the same frame edge for hours. Returns the offset in screen pixels.
     */
    fun drift(elapsedMinutes: Long): Pair<Int, Int> = DRIFT_PATH[Math.floorMod(elapsedMinutes / DRIFT_STEP_MIN, DRIFT_PATH.size.toLong()).toInt()]

    const val MAX_EVENING_DIM = 0.35f
    const val DRIFT_STEP_MIN = 3L
    private val DRIFT_PATH = listOf(0 to 0, 2 to 0, 2 to 2, 0 to 2, -2 to 2, -2 to 0, -2 to -2, 0 to -2, 2 to -2)
}
