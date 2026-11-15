package com.newtermux.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassTokensTest {

    @Test
    fun blurInterpolatesBetweenThinAndLarge() {
        assertEquals(GlassTokens.THIN_BLUR_DP, GlassTokens.blurForSize(GlassTokens.THIN_MAX_DP), 0.01f)
        assertEquals(GlassTokens.LARGE_BLUR_DP, GlassTokens.blurForSize(GlassTokens.LARGE_MIN_DP), 0.01f)
        assertEquals(
            (GlassTokens.THIN_BLUR_DP + GlassTokens.LARGE_BLUR_DP) * 0.5f,
            GlassTokens.blurForSize((GlassTokens.THIN_MAX_DP + GlassTokens.LARGE_MIN_DP) * 0.5f),
            0.01f,
        )
        // Clamped outside bounds
        assertEquals(GlassTokens.THIN_BLUR_DP, GlassTokens.blurForSize(10f), 0.01f)
        assertEquals(GlassTokens.LARGE_BLUR_DP, GlassTokens.blurForSize(500f), 0.01f)
    }

    @Test
    fun tintAlphaInterpolatesBetweenThinAndLarge() {
        assertEquals(GlassTokens.GLASS_TINT.alpha, GlassTokens.tintAlphaForSize(GlassTokens.THIN_MAX_DP), 0.01f)
        assertEquals(GlassTokens.LARGE_TINT_ALPHA, GlassTokens.tintAlphaForSize(GlassTokens.LARGE_MIN_DP), 0.01f)
    }

    @Test
    fun resolveGlassPathRespectsUserToggleAndAvailability() {
        assertEquals(GlassPath.LENS, resolveGlassPath(GlassAvailability.LENS, userSwitchOn = true))
        assertEquals(GlassPath.FROST, resolveGlassPath(GlassAvailability.LENS, userSwitchOn = false))
        assertEquals(GlassPath.LIVE_BLUR, resolveGlassPath(GlassAvailability.LIVE_BLUR, userSwitchOn = true))
        assertEquals(GlassPath.FROST, resolveGlassPath(GlassAvailability.LIVE_BLUR, userSwitchOn = false))
        assertEquals(GlassPath.FROST, resolveGlassPath(GlassAvailability.FROST, userSwitchOn = true))
    }
}
