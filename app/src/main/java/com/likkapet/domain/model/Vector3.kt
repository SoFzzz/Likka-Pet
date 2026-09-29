package com.likkapet.domain.model

/** A 3-axis sensor sample in m/s², in Android's device coordinates (x right, y up, z out of the screen). */
data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
)
