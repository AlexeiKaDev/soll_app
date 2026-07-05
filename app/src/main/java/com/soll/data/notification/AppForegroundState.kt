package com.soll.data.notification

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object AppForegroundState {
    private val registered = AtomicBoolean(false)
    private val resumedActivities = AtomicInteger(0)
    private val appInForeground = AtomicBoolean(false)
    private val currentRoute = AtomicReference<String?>(null)
    private val backgroundNotified = AtomicBoolean(true)
    private val backgroundListeners = CopyOnWriteArrayList<() -> Unit>()
    private val foregroundListeners = CopyOnWriteArrayList<() -> Unit>()

    fun register(application: Application) {
        if (!registered.compareAndSet(false, true)) return

        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumedActivities.incrementAndGet()
                    val wasInForeground = appInForeground.getAndSet(true)
                    backgroundNotified.set(false)
                    if (!wasInForeground) {
                        foregroundListeners.forEach { listener -> listener() }
                    }
                }

                override fun onActivityPaused(activity: Activity) {
                    val remaining = resumedActivities.updateAndGet { count ->
                        (count - 1).coerceAtLeast(0)
                    }
                    if (remaining == 0) {
                        markBackground()
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) {
                    if (resumedActivities.get() == 0) {
                        markBackground()
                    }
                }
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    fun addBackgroundListener(listener: () -> Unit) {
        backgroundListeners += listener
    }

    fun addForegroundListener(listener: () -> Unit) {
        foregroundListeners += listener
    }

    fun updateCurrentRoute(route: String?) {
        currentRoute.set(route)
    }

    fun isInForeground(): Boolean = appInForeground.get()

    fun isUserFacing(): Boolean {
        if (!appInForeground.get()) return false
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        return processInfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    fun activeRoute(): String? = currentRoute.get()

    private fun markBackground() {
        appInForeground.set(false)
        currentRoute.set(null)
        if (backgroundNotified.compareAndSet(false, true)) {
            backgroundListeners.forEach { listener -> listener() }
        }
    }
}
