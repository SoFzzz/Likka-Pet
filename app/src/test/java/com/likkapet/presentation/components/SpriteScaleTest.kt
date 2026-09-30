package com.likkapet.presentation.components

import com.likkapet.presentation.theme.LikkaSpriteSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Integer sprite scaling of design system §1.7: `floor(targetPx / 88)`, at Redmi 9 density (2.75). */
class SpriteScaleTest {
    private fun scaleFor(
        dp: Float,
        density: Float = REDMI_9_DENSITY,
    ) = spriteScale(dp * density)

    @Test
    fun `sprite size tokens give the scales of the design system table`() {
        assertEquals(2, scaleFor(LikkaSpriteSize.level1.value))
        assertEquals(4, scaleFor(LikkaSpriteSize.level2.value))
        assertEquals(6, scaleFor(LikkaSpriteSize.level3.value))
        assertEquals(5, scaleFor(LikkaSpriteSize.dashboard.value))
        assertEquals(3, scaleFor(LikkaSpriteSize.onboarding.value))
    }

    @Test
    fun `scale never rounds up`() {
        assertEquals(1, spriteScale(SpriteMetrics.CHARACTER_HEIGHT_PX * 2 - 1f))
        assertEquals(2, spriteScale(SpriteMetrics.CHARACTER_HEIGHT_PX * 2f))
    }

    @Test
    fun `a target smaller than the character still draws at 1x`() {
        assertEquals(1, spriteScale(1f))
    }

    @Test
    fun `stage circle of the frame is never smaller than the displayed character`() {
        val scale = scaleFor(LikkaSpriteSize.dashboard.value)
        assert(SpriteMetrics.FRAME_PX * scale >= SpriteMetrics.CHARACTER_HEIGHT_PX * scale)
    }

    private companion object {
        const val REDMI_9_DENSITY = 2.75f
    }
}
