package com.kairosera.core.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kairosera.core.ui.theme.Kairos

/** One-tap choice chip: navy when chosen, a quiet card when not. 40dp tall for easy tapping. */
@Composable
fun KChip(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Kairos.colors.brand,
            selectedLabelColor = Kairos.colors.onBrand,
            selectedLeadingIconColor = Kairos.colors.onBrand,
            containerColor = Kairos.colors.card,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Kairos.colors.line),
        modifier = Modifier.height(40.dp),
    )
}

