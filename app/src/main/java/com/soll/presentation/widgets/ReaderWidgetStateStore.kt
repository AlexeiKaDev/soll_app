package com.soll.presentation.widgets

import android.content.Context
import com.soll.data.repository.ReaderWidgetBookState

object ReaderWidgetStateStore {
    private const val PREFS_NAME = "reader_widget_state"
    private const val KEY_TITLE = "title"
    private const val KEY_SUBTITLE = "subtitle"
    private const val KEY_COVER_PATH = "cover_path"

    fun read(context: Context): ReaderWidgetBookState? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val subtitle = prefs.getString(KEY_SUBTITLE, null)
            ?.takeIf { it.isNotBlank() }
            ?: "Откройте книгу"
        val coverPath = prefs.getString(KEY_COVER_PATH, null)
            ?.takeIf { it.isNotBlank() }

        return ReaderWidgetBookState(
            title = title,
            subtitle = subtitle,
            coverPath = coverPath,
        )
    }

    fun write(context: Context, state: ReaderWidgetBookState) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, state.title)
            .putString(KEY_SUBTITLE, state.subtitle)
            .putString(KEY_COVER_PATH, state.coverPath.orEmpty())
            .apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
