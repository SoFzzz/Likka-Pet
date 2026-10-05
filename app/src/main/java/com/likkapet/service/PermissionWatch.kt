package com.likkapet.service

import com.likkapet.domain.port.PermissionChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Whether every required permission is granted, re-read every [periodMs] (a revoked one shows up in the notification, RF-A06). */
fun PermissionChecker.watchAllGranted(periodMs: Long): Flow<Boolean> =
    flow {
        while (true) {
            emit(requiredPermissions.all(::isGranted))
            delay(periodMs)
        }
    }.distinctUntilChanged()
