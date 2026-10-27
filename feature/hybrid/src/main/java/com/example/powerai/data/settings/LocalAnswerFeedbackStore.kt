package com.example.powerai.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.powerai.domain.model.LocalAnswerFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAnswerFeedbackStore internal constructor(
    private val prefs: SharedPreferences
) {

    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun getFeedback(query: String): LocalAnswerFeedback {
        val normalized = normalizeQuery(query) ?: return LocalAnswerFeedback.NONE
        val persisted = prefs.getString(prefKey(normalized), null)
        return persisted
            ?.let { value -> LocalAnswerFeedback.entries.firstOrNull { it.name == value } }
            ?: LocalAnswerFeedback.NONE
    }

    fun setFeedback(query: String, feedback: LocalAnswerFeedback) {
        val normalized = normalizeQuery(query) ?: return
        val key = prefKey(normalized)
        val editor = prefs.edit()
        if (feedback == LocalAnswerFeedback.NONE) {
            editor.remove(key)
        } else {
            editor.putString(key, feedback.name)
        }
        editor.apply()
    }

    private fun normalizeQuery(query: String): String? =
        query.trim().lowercase().takeIf { it.isNotEmpty() }

    private fun prefKey(normalizedQuery: String): String = KEY_PREFIX + sha256(normalizedQuery)

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val PREFS_NAME = "powerai_prefs"
        private const val KEY_PREFIX = "local_answer_feedback_"
    }
}
