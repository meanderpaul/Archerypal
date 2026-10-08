package com.archerypal.app.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archerypal.app.remote.FeedFilter
import com.archerypal.app.ui.theme.FieldGreen
import com.archerypal.app.ui.theme.TargetGold

enum class SocialHub {
    FEED,
    EVENTS,
    FRIENDS
}

private data class FilterChipItem(val filter: FeedFilter, val label: String)

private val eventFilterItems = listOf(
    FilterChipItem(FeedFilter.ALL, "All"),
    FilterChipItem(FeedFilter.NEARBY, "Nearby"),
    FilterChipItem(FeedFilter.FRIENDS, "Friend shoots"),
    FilterChipItem(FeedFilter.GLOBAL, "Regional")
)

private val postFilterItems = listOf(
    FilterChipItem(FeedFilter.FRIEND_POSTS, "Friends"),
    FilterChipItem(FeedFilter.REGIONAL_POSTS, "Regional")
)

val eventFilters: List<FeedFilter> = eventFilterItems.map { it.filter }
val postFilters: List<FeedFilter> = postFilterItems.map { it.filter }

@Composable
fun SocialHubBar(
    hub: SocialHub,
    feedNewCount: Int,
    eventsNewCount: Int,
    friendsNewCount: Int = 0,
    onSelect: (SocialHub) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            HubChip(
                label = "Feed",
                selected = hub == SocialHub.FEED,
                hasNew = feedNewCount > 0,
                onClick = { onSelect(SocialHub.FEED) }
            )
        }
        item {
            HubChip(
                label = "Events",
                selected = hub == SocialHub.EVENTS,
                hasNew = eventsNewCount > 0,
                onClick = { onSelect(SocialHub.EVENTS) }
            )
        }
        item {
            HubChip(
                label = "Friends",
                selected = hub == SocialHub.FRIENDS,
                hasNew = friendsNewCount > 0,
                onClick = { onSelect(SocialHub.FRIENDS) }
            )
        }
    }
}

@Composable
fun EventFilterBar(
    selected: FeedFilter,
    newCounts: Map<FeedFilter, Int>,
    onSelect: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChipRow(eventFilterItems, selected, newCounts, onSelect, modifier)
}

@Composable
fun PostFilterBar(
    selected: FeedFilter,
    newCounts: Map<FeedFilter, Int>,
    onSelect: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChipRow(postFilterItems, selected, newCounts, onSelect, modifier)
}

@Composable
private fun FilterChipRow(
    chips: List<FilterChipItem>,
    selected: FeedFilter,
    newCounts: Map<FeedFilter, Int>,
    onSelect: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips, key = { it.filter.name }) { item ->
            val hasNew = (newCounts[item.filter] ?: 0) > 0
            val isSelected = selected == item.filter
            Box {
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(item.filter) },
                    label = { Text(item.label) },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = FieldGreen.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = FieldGreen
                    )
                )
                if (hasNew && !isSelected) {
                    Surface(
                        color = TargetGold,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 2.dp, top = 2.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp))
                    }
                }
            }
        }
    }
}

/** @deprecated Prefer [SocialHubBar] + [EventFilterBar]. Kept for any leftover call sites. */
@Composable
fun FeedFilterBar(
    selected: FeedFilter,
    newCounts: Map<FeedFilter, Int>,
    onSelect: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SocialHubBar(
            hub = if (selected.isPostFilter) SocialHub.FEED else SocialHub.EVENTS,
            feedNewCount = postFilters.sumOf { newCounts[it] ?: 0 },
            eventsNewCount = eventFilters.sumOf { newCounts[it] ?: 0 },
            onSelect = { hub ->
                when (hub) {
                    SocialHub.FEED -> onSelect(FeedFilter.FRIEND_POSTS)
                    SocialHub.EVENTS, SocialHub.FRIENDS -> onSelect(FeedFilter.ALL)
                }
            }
        )
        if (selected.isPostFilter) {
            PostFilterBar(selected = selected, newCounts = newCounts, onSelect = onSelect)
        } else {
            EventFilterBar(selected = selected, newCounts = newCounts, onSelect = onSelect)
        }
    }
}

@Composable
private fun HubChip(
    label: String,
    selected: Boolean,
    hasNew: Boolean,
    onClick: () -> Unit
) {
    Box {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            },
            shape = RoundedCornerShape(999.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = FieldGreen,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = FieldGreen
            )
        )
        if (hasNew && !selected) {
            Surface(
                color = TargetGold,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp, top = 2.dp)
            ) {
                Box(modifier = Modifier.size(8.dp))
            }
        }
    }
}
