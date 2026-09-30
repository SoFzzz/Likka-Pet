package com.likkapet.presentation.onboarding

private val MIUI_BRANDS = setOf("xiaomi", "redmi", "poco")

/**
 * True for Xiaomi, Redmi and POCO phones, which is when onboarding shows the MIUI guide (RF-A05).
 * Brand-based on purpose: MIUI/HyperOS-specific system properties are not public API.
 */
fun isMiuiDevice(
    manufacturer: String,
    brand: String,
): Boolean = manufacturer.lowercase() in MIUI_BRANDS || brand.lowercase() in MIUI_BRANDS
