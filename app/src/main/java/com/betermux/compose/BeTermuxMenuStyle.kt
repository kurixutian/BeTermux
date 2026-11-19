package com.betermux.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betermux.theme.LocalWarpPalette
import com.betermux.theme.warpSurface

/**
 * Shared Warp card look for pop-out menus across BeTermux: flat surface
 * with crisp 1dp structural border and zero glass/blur.
 */
@Composable
fun Modifier.outlinedMenuCard(): Modifier {
    val palette = LocalWarpPalette.current
    return this.warpSurface(
        shape = RoundedCornerShape(10.dp),
        color = palette.surfaceElevated,
        borderColor = palette.borderStrong,
        borderWidth = 1.dp,
    )
}

/** Crisp 1dp hairline divider between menu items. */
@Composable
fun MenuItemDivider() {
    val palette = LocalWarpPalette.current
    HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
}
