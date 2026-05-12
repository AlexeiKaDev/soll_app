package com.soll.presentation.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.soll.R
import com.soll.data.repository.BookRepository
import com.soll.data.repository.ReaderWidgetBookState
import com.soll.data.service.TtsService
import com.soll.presentation.navigation.AppLaunchTargets
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderWidgetEntryPoint {
    fun bookRepository(): BookRepository
}

class ReaderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        update(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREVIOUS -> TtsService.previous(context)
            ACTION_PLAY_PAUSE -> TtsService.toggle(context)
            ACTION_NEXT -> TtsService.next(context)
            ACTION_STOP -> TtsService.stopPlayback(context)
            else -> return
        }
        updateAll(context)
    }

    companion object {
        private const val ACTION_PREVIOUS = "com.soll.widget.reader.PREVIOUS"
        private const val ACTION_PLAY_PAUSE = "com.soll.widget.reader.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.soll.widget.reader.NEXT"
        private const val ACTION_STOP = "com.soll.widget.reader.STOP"
        private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var refreshInFlight = false

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            update(context, manager, ToolWidgetIntents.widgetIds(context, ReaderWidgetProvider::class.java))
        }

        private fun update(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return

            val state = TtsService.currentState()
            val savedReader = if (state.isRunning) null else loadLastReadState(context)
                .also { if (it == null) refreshLastReadStateAsync(context) }
            val title = when {
                state.isRunning -> state.title
                savedReader != null -> savedReader.title
                else -> "Читалка"
            }
            val subtitle = when {
                savedReader != null -> savedReader.subtitle
                state.subtitle.isNotBlank() -> state.subtitle
                state.isRunning && state.isPlaying -> "Чтение вслух"
                state.isRunning -> "Пауза"
                else -> "Откройте книгу"
            }
            val playPauseIcon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            val playPauseLabel = when {
                state.isPlaying -> "Пауза"
                state.isRunning -> "Играть"
                else -> "Открыть читалку"
            }
            val coverPath = if (state.isRunning) state.coverPath else savedReader?.coverPath
            val openReader = ToolWidgetIntents.openToolActivity(
                context,
                AppLaunchTargets.SECTION_BOOK_READER,
                ToolWidgetIntents.REQUEST_READER_OPEN,
            )

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_reader).apply {
                    setTextViewText(R.id.widget_title, title)
                    setTextViewText(R.id.widget_subtitle, subtitle)
                    setImageViewResource(R.id.widget_play_pause, playPauseIcon)
                    setContentDescription(R.id.widget_play_pause, playPauseLabel)
                    WidgetArtworkLoader.setFileArtwork(
                        views = this,
                        viewId = R.id.widget_artwork,
                        filePath = coverPath,
                        fallbackResId = R.drawable.ic_widget_book,
                    )
                    setOnClickPendingIntent(R.id.widget_root, openReader)
                    setOnClickPendingIntent(
                        R.id.widget_previous,
                        if (state.isRunning) ToolWidgetIntents.broadcast(
                            context,
                            ReaderWidgetProvider::class.java,
                            ACTION_PREVIOUS,
                            ToolWidgetIntents.REQUEST_READER_PREV,
                        ) else openReader,
                    )
                    setOnClickPendingIntent(
                        R.id.widget_play_pause,
                        if (state.isRunning) ToolWidgetIntents.broadcast(
                            context,
                            ReaderWidgetProvider::class.java,
                            ACTION_PLAY_PAUSE,
                            ToolWidgetIntents.REQUEST_READER_TOGGLE,
                        ) else openReader,
                    )
                    setOnClickPendingIntent(
                        R.id.widget_next,
                        if (state.isRunning) ToolWidgetIntents.broadcast(
                            context,
                            ReaderWidgetProvider::class.java,
                            ACTION_NEXT,
                            ToolWidgetIntents.REQUEST_READER_NEXT,
                        ) else openReader,
                    )
                    setOnClickPendingIntent(
                        R.id.widget_stop,
                        if (state.isRunning) ToolWidgetIntents.broadcast(
                            context,
                            ReaderWidgetProvider::class.java,
                            ACTION_STOP,
                            ToolWidgetIntents.REQUEST_READER_STOP,
                        ) else openReader,
                    )
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun loadLastReadState(context: Context): ReaderWidgetBookState? =
            ReaderWidgetStateStore.read(context)

        private fun refreshLastReadStateAsync(context: Context) {
            if (refreshInFlight) return
            val appContext = context.applicationContext
            refreshInFlight = true
            refreshScope.launch {
                try {
                    val state = runCatching {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            appContext,
                            ReaderWidgetEntryPoint::class.java,
                        )
                        entryPoint.bookRepository().getLastReadWidgetState()
                    }.getOrNull()

                    if (state != null) {
                        ReaderWidgetStateStore.write(appContext, state)
                        updateAll(appContext)
                    }
                } finally {
                    refreshInFlight = false
                }
            }
        }
    }
}
