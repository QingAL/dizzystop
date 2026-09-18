package com.qingal.dizzystop

import android.content.Context
import android.content.SharedPreferences

object MotionCueSettings {
    const val KEY_DOT_COUNT = "dot_count"
    const val KEY_DOT_RADIUS_TENTHS_DP = "dot_radius_tenths_dp"
    const val KEY_EDGE_MARGIN_DP = "edge_margin_dp"
    const val KEY_OPACITY_PERCENT = "opacity_percent"
    const val KEY_DARK_DOTS = "dark_dots"
    const val KEY_SENSITIVITY_DP = "sensitivity_dp"
    const val KEY_MAX_OFFSET_DP = "max_offset_dp"
    const val KEY_SMOOTHING_PERCENT = "smoothing_percent"
    const val KEY_DEAD_ZONE_HUNDREDTHS = "dead_zone_hundredths"
    const val KEY_FILTER_TIME_HUNDREDTHS = "filter_time_hundredths"
    const val KEY_ROTATION_THRESHOLD_TENTHS = "rotation_threshold_tenths"
    const val KEY_ROTATION_SETTLE_HUNDREDS_MS = "rotation_settle_hundreds_ms"

    const val DEFAULT_DOT_COUNT = 6
    const val DEFAULT_DOT_RADIUS_TENTHS_DP = 45
    const val DEFAULT_EDGE_MARGIN_DP = 18
    const val DEFAULT_OPACITY_PERCENT = 65
    const val DEFAULT_DARK_DOTS = false
    const val DEFAULT_SENSITIVITY_DP = 18
    const val DEFAULT_MAX_OFFSET_DP = 35
    const val DEFAULT_SMOOTHING_PERCENT = 10
    const val DEFAULT_DEAD_ZONE_HUNDREDTHS = 8
    const val DEFAULT_FILTER_TIME_HUNDREDTHS = 28
    const val DEFAULT_ROTATION_THRESHOLD_TENTHS = 8
    const val DEFAULT_ROTATION_SETTLE_HUNDREDS_MS = 4

    data class Values(
        val dotCount: Int,
        val dotRadiusDp: Float,
        val edgeMarginDp: Float,
        val opacity: Float,
        val darkDots: Boolean,
        val sensitivityDp: Float,
        val maxOffsetDp: Float,
        val smoothing: Float,
        val deadZone: Float,
        val filterTimeSeconds: Float,
        val rotationThreshold: Float,
        val rotationSettleSeconds: Float
    )

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): Values = read(preferences(context))

    fun read(preferences: SharedPreferences) = Values(
        dotCount = preferences.getInt(KEY_DOT_COUNT, DEFAULT_DOT_COUNT).coerceIn(4, 10),
        dotRadiusDp = preferences.getInt(
            KEY_DOT_RADIUS_TENTHS_DP,
            DEFAULT_DOT_RADIUS_TENTHS_DP
        ).coerceIn(30, 100) / 10f,
        edgeMarginDp = preferences.getInt(
            KEY_EDGE_MARGIN_DP,
            DEFAULT_EDGE_MARGIN_DP
        ).coerceIn(8, 40).toFloat(),
        opacity = preferences.getInt(
            KEY_OPACITY_PERCENT,
            DEFAULT_OPACITY_PERCENT
        ).coerceIn(30, 100) / 100f,
        darkDots = preferences.getBoolean(KEY_DARK_DOTS, DEFAULT_DARK_DOTS),
        sensitivityDp = preferences.getInt(
            KEY_SENSITIVITY_DP,
            DEFAULT_SENSITIVITY_DP
        ).coerceIn(8, 32).toFloat(),
        maxOffsetDp = preferences.getInt(
            KEY_MAX_OFFSET_DP,
            DEFAULT_MAX_OFFSET_DP
        ).coerceIn(16, 80).toFloat(),
        smoothing = preferences.getInt(
            KEY_SMOOTHING_PERCENT,
            DEFAULT_SMOOTHING_PERCENT
        ).coerceIn(5, 35) / 100f,
        deadZone = preferences.getInt(
            KEY_DEAD_ZONE_HUNDREDTHS,
            DEFAULT_DEAD_ZONE_HUNDREDTHS
        ).coerceIn(2, 30) / 100f,
        filterTimeSeconds = preferences.getInt(
            KEY_FILTER_TIME_HUNDREDTHS,
            DEFAULT_FILTER_TIME_HUNDREDTHS
        ).coerceIn(10, 80) / 100f,
        rotationThreshold = preferences.getInt(
            KEY_ROTATION_THRESHOLD_TENTHS,
            DEFAULT_ROTATION_THRESHOLD_TENTHS
        ).coerceIn(3, 20) / 10f,
        rotationSettleSeconds = preferences.getInt(
            KEY_ROTATION_SETTLE_HUNDREDS_MS,
            DEFAULT_ROTATION_SETTLE_HUNDREDS_MS
        ).coerceIn(1, 10) / 10f
    )

    fun reset(context: Context) {
        preferences(context).edit().clear().apply()
    }

    private const val PREFERENCES_NAME = "motion_cue_settings"
}
