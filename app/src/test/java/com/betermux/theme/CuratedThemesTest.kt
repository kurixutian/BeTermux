package com.betermux.theme

import android.content.Context
import android.provider.Settings
import com.betermux.features.BeTermuxColorTheme
import com.betermux.features.BeTermuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CuratedThemesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("betermux_color_theme", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("betermux_background", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("betermux_theme", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun curatedThemesListHasExactlyThreeCuratedEntries() {
        assertEquals(3, CuratedThemes.ALL.size)
        assertEquals(
            listOf(
                CuratedThemes.KEY_WARM_DARK,
                CuratedThemes.KEY_SOLARIZED_DARK,
                CuratedThemes.KEY_HIGH_CONTRAST,
            ),
            CuratedThemes.ALL.map { it.key },
        )
    }

    @Test
    fun normalizeKeyMapsLegacyThemesAndNullToWarmDark() {
        assertEquals(CuratedThemes.KEY_WARM_DARK, CuratedThemes.normalizeKey(null))
        assertEquals(CuratedThemes.KEY_WARM_DARK, CuratedThemes.normalizeKey("default_dark"))
        assertEquals(CuratedThemes.KEY_WARM_DARK, CuratedThemes.normalizeKey("dracula"))
        assertEquals(CuratedThemes.KEY_SOLARIZED_DARK, CuratedThemes.normalizeKey("solarized_dark"))
        assertEquals(CuratedThemes.KEY_HIGH_CONTRAST, CuratedThemes.normalizeKey("high_contrast"))
        assertEquals(CuratedThemes.KEY_CUSTOM, CuratedThemes.normalizeKey("custom"))
    }

    @Test
    fun highContrastMeetsWcagAaaAcrossAllSurfaces() {
        val hc = CuratedThemes.HighContrast
        val surfaces = listOf(hc.background, hc.surface, hc.surfaceElevated)
        for (bg in surfaces) {
            assertTrue(
                "primary text vs $bg contrast=${CuratedThemes.contrastRatio(hc.textPrimary, bg)}",
                CuratedThemes.contrastRatio(hc.textPrimary, bg) >= 7.0,
            )
            assertTrue(
                "secondary text vs $bg contrast=${CuratedThemes.contrastRatio(hc.textSecondary, bg)}",
                CuratedThemes.contrastRatio(hc.textSecondary, bg) >= 7.0,
            )
            assertTrue(
                "muted text vs $bg contrast=${CuratedThemes.contrastRatio(hc.textMuted, bg)}",
                CuratedThemes.contrastRatio(hc.textMuted, bg) >= 7.0,
            )
            assertTrue(
                "default accent vs $bg contrast=${CuratedThemes.contrastRatio(hc.defaultAccent, bg)}",
                CuratedThemes.contrastRatio(hc.defaultAccent, bg) >= 7.0,
            )
        }
    }

    @Test
    fun warmDarkAndSolarizedDarkMeetWcagAaForPrimaryAndSecondaryText() {
        for (theme in listOf(CuratedThemes.WarmDark, CuratedThemes.SolarizedDark)) {
            for (bg in listOf(theme.background, theme.surface, theme.surfaceElevated)) {
                assertTrue(
                    "${theme.key} primary contrast",
                    CuratedThemes.contrastRatio(theme.textPrimary, bg) >= 7.0,
                )
                assertTrue(
                    "${theme.key} secondary contrast=${CuratedThemes.contrastRatio(theme.textSecondary, bg)}",
                    CuratedThemes.contrastRatio(theme.textSecondary, bg) >= 4.5,
                )
            }
        }
    }

    @Test
    fun colorThemePersistsAndWritesValidProperties() {
        assertEquals(CuratedThemes.KEY_WARM_DARK, BeTermuxColorTheme.getCurrentTheme(context))

        BeTermuxColorTheme.applyTheme(context, CuratedThemes.KEY_SOLARIZED_DARK)
        assertEquals(CuratedThemes.KEY_SOLARIZED_DARK, BeTermuxColorTheme.getCurrentTheme(context))

        val propsText = BeTermuxColorTheme.getThemeContent(context, CuratedThemes.KEY_HIGH_CONTRAST)
        assertTrue(propsText.contains("background=#000000"))
        assertTrue(propsText.contains("foreground=#faf9f6"))
        for (idx in 0..15) {
            assertTrue("Missing color$idx", propsText.contains("color$idx=#"))
        }

        val custom = "background=#111111\nforeground=#faf9f6\ncursor=#d49b54\n"
        BeTermuxColorTheme.applyCustomTheme(context, custom)
        assertEquals(BeTermuxColorTheme.THEME_KEY_CUSTOM, BeTermuxColorTheme.getCurrentTheme(context))
        assertEquals(custom, BeTermuxColorTheme.getThemeContent(context, BeTermuxColorTheme.THEME_KEY_CUSTOM))
    }

    @Test
    fun previewColorsMatchJavaSurfaceAccessors() {
        for (key in listOf(
            CuratedThemes.KEY_WARM_DARK,
            CuratedThemes.KEY_SOLARIZED_DARK,
            CuratedThemes.KEY_HIGH_CONTRAST,
        )) {
            val preview = BeTermuxColorTheme.getPreviewColors(key)
            assertEquals(5, preview.size)
            assertEquals(preview[0], BeTermuxColorTheme.getBackgroundColorInt(key))
            assertEquals(preview[2], BeTermuxColorTheme.getSurfaceColorInt(key))
            assertNotNull(BeTermuxColorTheme.getSurfaceElevatedColorInt(key))
            assertNotNull(BeTermuxColorTheme.getBorderSubtleColorInt(key))
        }
        assertEquals(0xFFD49B54.toInt(), BeTermuxTheme.DEFAULT_COLOR)
    }

    @Test
    fun backgroundScrimClampsAndPersistsAcrossReads() {
        BackgroundImageStore.clearImage(context)
        assertFalse(BackgroundImageStore.hasImage(context))
        assertEquals(BackgroundImageStore.DEFAULT_SCRIM_ALPHA, BackgroundImageStore.getScrimAlpha(context), 0.001f)

        BackgroundImageStore.setScrimAlpha(context, 0.10f)
        assertEquals(BackgroundImageStore.MIN_SCRIM_ALPHA, BackgroundImageStore.getScrimAlpha(context), 0.001f)

        BackgroundImageStore.setScrimAlpha(context, 1.50f)
        assertEquals(BackgroundImageStore.MAX_SCRIM_ALPHA, BackgroundImageStore.getScrimAlpha(context), 0.001f)

        val scrimInt = BackgroundImageStore.computeScrimColorInt(0xFF121210.toInt(), 0.80f)
        assertEquals(204, android.graphics.Color.alpha(scrimInt))
    }

    @Test
    fun motionPreferencesRespectsSystemAnimatorScale() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(MotionPreferences.shouldReduceMotion(context))

        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        assertFalse(MotionPreferences.shouldReduceMotion(context))
    }
}
