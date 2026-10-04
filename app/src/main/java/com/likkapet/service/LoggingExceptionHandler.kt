package com.likkapet.service

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Last resort for a coroutine that fails on a service or application scope: log the exception class
 * (never its message, which could carry user data) instead of letting it kill the process, so one
 * failed write or poll does not take the monitoring down with it.
 */
fun loggingExceptionHandler(tag: String) =
    CoroutineExceptionHandler { _, throwable -> Log.e(tag, "Coroutine failed: ${throwable::class.simpleName}") }
