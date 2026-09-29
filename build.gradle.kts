plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied to any module (AGP 9 compiles Kotlin itself); declared here only so the
    // build uses the Kotlin version from the catalog instead of AGP's bundled minimum.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
}
