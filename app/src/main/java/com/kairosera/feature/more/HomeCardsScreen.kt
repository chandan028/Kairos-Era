package com.kairosera.feature.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.settings.AppSettings
import com.kairosera.core.settings.HomeCard
import com.kairosera.ui.appContainer
import kotlinx.coroutines.launch

@Composable
fun HomeCardsScreen(settings: AppSettings, onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    fun save(order: List<HomeCard>, hidden: Set<HomeCard>) = scope.launch { c.settings.setHomeCards(order, hidden) }
    SubScreen(stringResource(R.string.home_customize), onBack) {
        Column {
            Text(
                stringResource(R.string.home_customize_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            val order = settings.homeCards
            order.forEachIndexed { i, card ->
                ListItem(
                    headlineContent = { Text(stringResource(cardLabel(card))) },
                    leadingContent = {
                        Switch(checked = card !in settings.hiddenCards, onCheckedChange = { visible ->
                            save(order, if (visible) settings.hiddenCards - card else settings.hiddenCards + card)
                        })
                    },
                    trailingContent = {
                        Row {
                            IconButton(enabled = i > 0, onClick = { save(order.toMutableList().apply { add(i - 1, removeAt(i)) }, settings.hiddenCards) }) {
                                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up))
                            }
                            IconButton(enabled = i < order.lastIndex, onClick = { save(order.toMutableList().apply { add(i + 1, removeAt(i)) }, settings.hiddenCards) }) {
                                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down))
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun cardLabel(card: HomeCard) = when (card) {
    HomeCard.PROGRESS -> R.string.home_today_progress
    HomeCard.NEXT_UP -> R.string.home_next_up
    HomeCard.QUOTE -> R.string.home_daily_motivation
    HomeCard.TRACKERS -> R.string.home_trackers
}
