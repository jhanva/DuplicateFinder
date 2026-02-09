package com.duplicatefinder.util.extension

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.formatFileSize(): String {
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", this / 1024.0)
        this < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", this / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", this / (1024.0 * 1024.0 * 1024.0))
    }
}

fun Long.formatDate(pattern: String = "MMM dd, yyyy"): String {
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date(this * 1000))
}

fun Long.formatDateTime(pattern: String = "MMM dd, yyyy HH:mm"): String {
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date(this * 1000))
}

fun Long.toRelativeTimeString(): String {
    val now = System.currentTimeMillis()
    val diffMs = now - this

    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 30 -> formatDate()
        days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
        hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
        else -> "Just now"
    }
}

fun String.truncateMiddle(maxLength: Int): String {
    if (length <= maxLength) return this

    val keepLength = (maxLength - 3) / 2
    return "${take(keepLength)}...${takeLast(keepLength)}"
}

fun Float.toPercentageString(): String {
    return String.format(Locale.US, "%.0f%%", this * 100)
}

fun Int.pluralize(singular: String, plural: String = "${singular}s"): String {
    return if (this == 1) "$this $singular" else "$this $plural"
}
