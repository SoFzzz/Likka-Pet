package com.likkapet.data

import com.likkapet.domain.port.ScreenStateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A screen the test turns on and off by hand. */
class FakeScreenStateSource(
    initiallyOn: Boolean = true,
) : ScreenStateSource {
    private val state = MutableStateFlow(initiallyOn)

    override val isScreenOn: Flow<Boolean> = state

    var isOn: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }
}
