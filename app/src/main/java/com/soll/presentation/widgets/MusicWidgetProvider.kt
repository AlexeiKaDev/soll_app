package com.soll.presentation.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.soll.R
import com.soll.data.service.MusicPlaybackService
import com.soll.presentation.navigation.AppLaunchTargets

class MusicWidgetProvider : AppWidgetProvider() {

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
            ACTION_PREVIOUS -> MusicPlaybackService.previous(context)
            ACTION_PLAY_PAUSE -> {
                if (MusicPlaybackService.currentState().isPlaying) {
                    MusicPlaybackService.pause(context)
                } else {
                    MusicPlaybackService.play(context)
                }
            }
            ACTION_NEXT -> MusicPlaybackService.next(context)
            ACTION_STOP -> MusicPlaybackService.stop(context)
            else -> return
        }
        updateAll(context)
    }

    companion object {
        private const val ACTION_PREVIOUS = "com.soll.widget.music.PREVIOUS"
        private const val ACTION_PLAY_PAUSE = "com.soll.widget.music.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.soll.widget.music.NEXT"
        private const val ACTION_STOP = "com.soll.widget.music.STOP"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            update(context, manager, ToolWidgetIntents.widgetIds(context, MusicWidgetProvider::class.java))
        }

        private fun update(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return

            val state = MusicPlaybackService.currentState()
            val title = when {
                state.currentTrackId != null -> state.title
                state.isPreparing -> "Музыка"
                else -> "Музыка Soll"
            }
            val subtitle = when {
                state.errorMessage != null -> state.errorMessage
                state.artist != null -> state.artist
                state.isPreparing -> state.statusText ?: "Готовлю воспроизведение"
                state.isServiceActive -> if (state.isPlaying) "Играет" else "Пауза"
                else -> "Играть продолжит последнюю очередь"
            }
            val playPauseIcon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            val playPauseLabel = if (state.isPlaying) "Пауза" else "Играть"

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_music).apply {
                    setTextViewText(R.id.widget_title, title)
                    setTextViewText(R.id.widget_subtitle, subtitle)
                    setImageViewResource(R.id.widget_play_pause, playPauseIcon)
                    setContentDescription(R.id.widget_play_pause, playPauseLabel)
                    WidgetArtworkLoader.setAudioArtwork(
                        context = context,
                        views = this,
                        viewId = R.id.widget_artwork,
                        trackUri = state.currentTrackUri,
                        fallbackResId = R.drawable.ic_widget_music,
                    )
                    setOnClickPendingIntent(
                        R.id.widget_root,
                        ToolWidgetIntents.openToolActivity(
                            context,
                            AppLaunchTargets.SECTION_MUSIC,
                            ToolWidgetIntents.REQUEST_MUSIC_OPEN,
                        ),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_previous,
                        ToolWidgetIntents.broadcast(
                            context,
                            MusicWidgetProvider::class.java,
                            ACTION_PREVIOUS,
                            ToolWidgetIntents.REQUEST_MUSIC_PREV,
                        ),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_play_pause,
                        ToolWidgetIntents.broadcast(
                            context,
                            MusicWidgetProvider::class.java,
                            ACTION_PLAY_PAUSE,
                            ToolWidgetIntents.REQUEST_MUSIC_TOGGLE,
                        ),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_next,
                        ToolWidgetIntents.broadcast(
                            context,
                            MusicWidgetProvider::class.java,
                            ACTION_NEXT,
                            ToolWidgetIntents.REQUEST_MUSIC_NEXT,
                        ),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_stop,
                        ToolWidgetIntents.broadcast(
                            context,
                            MusicWidgetProvider::class.java,
                            ACTION_STOP,
                            ToolWidgetIntents.REQUEST_MUSIC_STOP,
                        ),
                    )
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
