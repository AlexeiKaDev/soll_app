package com.soll.presentation.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.soll.presentation.MainActivity
import com.soll.presentation.navigation.AppLaunchTargets

internal object ToolWidgetIntents {
    const val REQUEST_MUSIC_OPEN = 8100
    const val REQUEST_MUSIC_PREV = 8101
    const val REQUEST_MUSIC_TOGGLE = 8102
    const val REQUEST_MUSIC_NEXT = 8103
    const val REQUEST_MUSIC_STOP = 8104

    const val REQUEST_READER_OPEN = 8200
    const val REQUEST_READER_PREV = 8201
    const val REQUEST_READER_TOGGLE = 8202
    const val REQUEST_READER_NEXT = 8203
    const val REQUEST_READER_STOP = 8204

    const val REQUEST_NOTES_OPEN = 8300

    private val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun openToolActivity(
        context: Context,
        section: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                putExtra(AppLaunchTargets.EXTRA_OPEN_SECTION, section)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            pendingFlags,
        )

    fun broadcast(
        context: Context,
        providerClass: Class<*>,
        action: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, providerClass).setAction(action),
            pendingFlags,
        )

    fun <T> widgetIds(context: Context, providerClass: Class<T>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, providerClass))
}
