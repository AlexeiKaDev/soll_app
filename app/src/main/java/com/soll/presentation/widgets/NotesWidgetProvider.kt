package com.soll.presentation.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.soll.R
import com.soll.presentation.navigation.AppLaunchTargets

class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            update(context, manager, ToolWidgetIntents.widgetIds(context, NotesWidgetProvider::class.java))
        }

        private fun update(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return

            val openNotes = ToolWidgetIntents.openToolActivity(
                context,
                AppLaunchTargets.SECTION_NOTES,
                ToolWidgetIntents.REQUEST_NOTES_OPEN,
            )
            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_notes).apply {
                    setOnClickPendingIntent(R.id.widget_root, openNotes)
                    setOnClickPendingIntent(R.id.widget_open, openNotes)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
