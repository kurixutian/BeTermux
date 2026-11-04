package com.newtermux.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Shared outlined-card look for the app's pop-out menus, mirroring Bannerlator's MenuStyle:
 * a rounded (12dp) card filled with the surface color, subtle elevation shadow, and a 1dp outline.
 * Apply to a DropdownMenu's `modifier` so the popup reads as an elevated outlined card.
 */
@Composable
fun Modifier.outlinedMenuCard(): Modifier {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    return this
        .shadow(6.dp, shape)
        .clip(shape)
        .background(cs.surface)
        .border(1.dp, cs.outlineVariant, shape)
}

/** The thin separator that sits between options inside an outlined menu card / option list. */
@Composable
fun MenuItemDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}
