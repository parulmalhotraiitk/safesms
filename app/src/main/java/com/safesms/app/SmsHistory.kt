package com.safesms.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

data class SmsHistoryEntry(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val sender: String = "Manual Input",
    val result: VerificationResult,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("MMM dd • HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val preview: String
        get() = if (message.length > 80) message.take(80) + "…" else message
}

object SmsHistory {
    private val _entries = MutableStateFlow<List<SmsHistoryEntry>>(emptyList())
    val entries: StateFlow<List<SmsHistoryEntry>> = _entries

    fun add(entry: SmsHistoryEntry) {
        _entries.value = listOf(entry) + _entries.value.take(99)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
