package com.newtermux.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newtermux.glass.GlassTokens
import com.newtermux.glass.glassSurface

/**
 * Shared Liquid Glass card look for pop-out menus across BeTermux.
 */
@Composable
fun Modifier.outlinedMenuCard(): Modifier =
    this.glassSurface(
        shape = RoundedCornerShape(14.dp),
        elevation = 8.dp,
        tint = GlassTokens.GLASS_TINT.copy(alpha = 0.82f),
        sampleBackdrop = false,
    )

/** The thin separator that sits between options inside a glass menu card / option list. */
@Composable
fun MenuItemDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
