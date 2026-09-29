package com.likkapet.domain.port

import kotlinx.coroutines.flow.Flow

/** Screen on/off (documentación §4.3); emits the current state first, then every change. */
interface ScreenStateSource {
    val isScreenOn: Flow<Boolean>
}
