package com.qiubo.optimaltv.voice

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// android/app/src/main/java/com/qiubo/optimaltv/voice/VoiceMatchIntents.kt

/** Contract shared with the voice helper for opening a named live football match. */
object VoiceMatchIntents {
    const val ACTION_VOICE_OPEN_MATCH = "com.qiubo.optimaltv.action.VOICE_OPEN_MATCH"
    const val EXTRA_QUERY = "query"
    private const val MAX_QUERY_LENGTH = 80

    fun readQuery(intent: Intent?): String? {
        if (intent == null || intent.action != ACTION_VOICE_OPEN_MATCH) return null
        return normalize(intent.getStringExtra(EXTRA_QUERY))
    }

    fun clear(intent: Intent) {
        intent.removeExtra(EXTRA_QUERY)
    }

    private fun normalize(query: String?): String? {
        val value = query?.trim().orEmpty()
        if (value.isEmpty()) return null
        return value.take(MAX_QUERY_LENGTH)
    }
}

data class VoiceMatchRequest(
    val id: Long,
    val query: String,
)

/**
 * Holds at most one pending voice match request until the live schedule is ready.
 * Requests are consumed by id so activity/composition recreation cannot replay an old match.
 */
object VoiceMatchRequestBus {
    private val _pending = MutableStateFlow<VoiceMatchRequest?>(null)
    val pending: StateFlow<VoiceMatchRequest?> = _pending.asStateFlow()
    private var nextId = 0L

    @Synchronized
    fun offer(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isEmpty()) return false
        nextId += 1
        _pending.value = VoiceMatchRequest(nextId, normalized)
        return true
    }

    fun consume(id: Long): Boolean {
        val current = _pending.value ?: return false
        if (current.id != id) return false
        return _pending.compareAndSet(current, null)
    }
}
