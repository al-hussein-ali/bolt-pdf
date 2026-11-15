package com.velocity.pdf.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.velocity.pdf.DevicePdfItem
import com.velocity.pdf.HomeDateFilter
import com.velocity.pdf.HomeProgressFilter
import com.velocity.pdf.HomeSizeFilter
import com.velocity.pdf.HomeSortOption
import com.velocity.pdf.ui.AppStrings
import uniffi.pdf_core.ReadingProgress
import java.util.Calendar
import java.util.Locale

internal fun localizedDateLabel(filter: HomeDateFilter, strings: AppStrings): String = when (filter) {
    HomeDateFilter.ALL -> strings.filterAllTime
    HomeDateFilter.TODAY -> strings.filterToday
    HomeDateFilter.LAST_7_DAYS -> strings.filterLast7Days
    HomeDateFilter.LAST_30_DAYS -> strings.filterLast30Days
    HomeDateFilter.THIS_YEAR -> strings.filterThisYear
}

internal fun localizedProgressLabel(filter: HomeProgressFilter, strings: AppStrings): String = when (filter) {
    HomeProgressFilter.ALL -> strings.filterAll
    HomeProgressFilter.UNREAD -> strings.filterUnread
    HomeProgressFilter.IN_PROGRESS -> strings.filterInProgress
    HomeProgressFilter.COMPLETED -> strings.filterFinished
}

internal fun localizedSizeLabel(filter: HomeSizeFilter, strings: AppStrings): String = when (filter) {
    HomeSizeFilter.ALL -> strings.filterAllSizes
    HomeSizeFilter.SMALL -> strings.filterUnder1Mb
    HomeSizeFilter.MEDIUM -> strings.filter1To10Mb
    HomeSizeFilter.LARGE -> strings.filterOver10Mb
}

internal fun sortRecentItems(
    items: List<ReadingProgress>,
    sortOption: HomeSortOption,
): List<ReadingProgress> = when (sortOption) {
    HomeSortOption.RECENTLY_OPENED -> items.sortedByDescending { it.lastOpenedUnix }
    HomeSortOption.OLDEST_FIRST -> items.sortedBy { it.lastOpenedUnix }
    HomeSortOption.NAME_ASC -> items.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    HomeSortOption.NAME_DESC -> items.sortedByDescending { it.displayName.lowercase(Locale.getDefault()) }
    HomeSortOption.SIZE_OR_PAGES -> items.sortedByDescending { it.totalPages }
    HomeSortOption.SMALLEST_OR_FEWEST -> items.sortedBy { it.totalPages }
}

internal fun sortDeviceItems(
    items: List<DevicePdfItem>,
    sortOption: HomeSortOption,
): List<DevicePdfItem> = when (sortOption) {
    HomeSortOption.RECENTLY_OPENED -> items.sortedByDescending { it.dateModifiedSec }
    HomeSortOption.OLDEST_FIRST -> items.sortedBy { it.dateModifiedSec }
    HomeSortOption.NAME_ASC -> items.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    HomeSortOption.NAME_DESC -> items.sortedByDescending { it.displayName.lowercase(Locale.getDefault()) }
    HomeSortOption.SIZE_OR_PAGES -> items.sortedByDescending { it.sizeBytes }
    HomeSortOption.SMALLEST_OR_FEWEST -> items.sortedBy { it.sizeBytes }
}

internal fun matchesDateFilter(timestampSec: Long, filter: HomeDateFilter): Boolean {
    if (filter == HomeDateFilter.ALL) return true
    if (timestampSec <= 0L) return false
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfTodaySec = cal.timeInMillis / 1000L
    val cutoffSec = when (filter) {
        HomeDateFilter.ALL -> 0L
        HomeDateFilter.TODAY -> startOfTodaySec
        HomeDateFilter.LAST_7_DAYS -> startOfTodaySec - 6L * 86_400L
        HomeDateFilter.LAST_30_DAYS -> startOfTodaySec - 29L * 86_400L
        HomeDateFilter.THIS_YEAR -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.timeInMillis / 1000L
        }
    }
    return timestampSec >= cutoffSec
}

internal fun matchesProgressFilter(item: ReadingProgress, filter: HomeProgressFilter): Boolean {
    if (filter == HomeProgressFilter.ALL) return true
    val totalPages = item.totalPages.toInt().coerceAtLeast(1)
    val currentPage = (item.lastPageIndex.toInt() + 1).coerceIn(1, totalPages)
    return when (filter) {
        HomeProgressFilter.ALL -> true
        HomeProgressFilter.UNREAD -> item.lastPageIndex == 0u && totalPages > 1
        HomeProgressFilter.IN_PROGRESS -> currentPage in 2 until totalPages
        HomeProgressFilter.COMPLETED -> currentPage >= totalPages
    }
}

internal fun matchesSizeFilter(sizeBytes: Long, filter: HomeSizeFilter): Boolean {
    if (filter == HomeSizeFilter.ALL) return true
    val oneMb = 1024L * 1024L
    val tenMb = 10L * oneMb
    return when (filter) {
        HomeSizeFilter.ALL -> true
        HomeSizeFilter.SMALL -> sizeBytes in 1L until oneMb
        HomeSizeFilter.MEDIUM -> sizeBytes in oneMb..tenMb
        HomeSizeFilter.LARGE -> sizeBytes > tenMb
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

@Composable
internal fun <T> FilterDropdownChip(
    label: String,
    isActive: Boolean,
    leadingIcon: ImageVector?,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = isActive,
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = { Text(label) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
