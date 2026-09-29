package com.likkapet.domain.port

/**
 * Source of the current time for domain classes (documentación §9.5), so JVM tests can move time
 * forward with a fake clock instead of waiting.
 */
fun interface Clock {
    fun nowMillis(): Long
}
