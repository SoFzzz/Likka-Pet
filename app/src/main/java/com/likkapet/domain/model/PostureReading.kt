package com.likkapet.domain.model

/**
 * One posture sample for the coordinator. The angle and the table classification come from
 * `BiomechanicsCalculator` (documentación §4); the coordinator only applies the thresholds.
 */
data class PostureReading(
    val angleDegrees: Double,
    val isOnTable: Boolean,
    // Gravity mostly along the phone's long axis: held sideways (landscape) uses its own thresholds.
    val isLandscape: Boolean = false,
)
