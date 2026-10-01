package com.likkapet.domain.model

/** An installed app with a launcher icon (RF-S06). Only ever stored or shown locally, never sent anywhere (§8). */
data class InstalledApp(
    val packageName: String,
    val label: String,
)
