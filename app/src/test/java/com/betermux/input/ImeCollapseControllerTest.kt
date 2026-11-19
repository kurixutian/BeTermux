package com.betermux.input

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImeCollapseControllerTest {

    private lateinit var activity: Activity
    private lateinit var root: LinearLayout
    private lateinit var main: LinearLayout
    private lateinit var bars: List<TextView>
    private lateinit var controller: ImeCollapseController

    @Before
    fun setUp() {
        activity = Robolectric.setupActivity(Activity::class.java)
        root = LinearLayout(ApplicationProvider.getApplicationContext())
        main = LinearLayout(ApplicationProvider.getApplicationContext())
        bars = (0 until 3).map { TextView(ApplicationProvider.getApplicationContext()) }
        for (bar in bars) root.addView(bar)
        root.addView(
            main,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        activity.setContentView(root)
        // Real view ids start at 1 in this test hierarchy; main container is 99
        main.id = 99
        bars.forEachIndexed { index, bar -> bar.id = index + 1 }
        controller = ImeCollapseController(activity, 99, 1, 2, 3)
    }

    @Test
    fun collapseShrinksMainToKeepFocusAndHidesBars() {
        controller.setCollapsed(true)

        assertTrue(controller.isCollapsed)
        assertEquals(1, main.layoutParams.height)
        assertEquals(0f, (main.layoutParams as LinearLayout.LayoutParams).weight, 0f)
        assertEquals(View.VISIBLE, main.visibility)
        bars.forEach { assertEquals(View.GONE, it.visibility) }
    }

    @Test
    fun restoreRevertsBarVisibilitiesSnapshotAndMainSize() {
        bars[1].visibility = View.GONE // non-default state must survive collapse/restore
        controller.setCollapsed(true)
        controller.setCollapsed(false)

        assertFalse(controller.isCollapsed)
        assertEquals(0, main.layoutParams.height)
        assertEquals(1f, (main.layoutParams as LinearLayout.LayoutParams).weight, 0f)
        assertEquals(View.VISIBLE, bars[0].visibility)
        assertEquals(View.GONE, bars[1].visibility)
        assertEquals(View.VISIBLE, bars[2].visibility)
    }
}
