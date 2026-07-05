package com.soll.data.service

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.soll.data.repository.SettingsRepository
import com.soll.domain.soll.SollGateway
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

object AndroidPushTokenRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentToken(
        context: Context,
        reason: String,
        force: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        val settings = entryPoint(appContext).settingsRepository()
        if (settings.sollServerUrl.isBlank()) {
            if (onFinished != null) {
                settings.sollPushTokenLastError = "Soll server URL is blank"
            }
            Timber.i("Skipping FCM token registration: Soll server URL is blank")
            onFinished?.invoke()
            return
        }

        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            settings.sollPushTokenLastError = "Firebase project config missing: google-services.json/google_app_id"
            Timber.w("Firebase project config is missing; FCM token cannot be created")
            onFinished?.invoke()
            return
        }

        val messaging = runCatching { FirebaseMessaging.getInstance() }
            .onFailure { error ->
                settings.sollPushTokenLastError = "Firebase unavailable: ${error.message.orEmpty()}"
                Timber.w(error, "Firebase Messaging is not configured")
            }
            .getOrNull() ?: run {
                onFinished?.invoke()
                return
            }

        messaging.token
            .addOnSuccessListener { token ->
                registerToken(appContext, token, reason = reason, force = force, onFinished = onFinished)
            }
            .addOnFailureListener { error ->
                settings.sollPushTokenLastError = "Token fetch failed: ${error.message.orEmpty()}"
                Timber.w(error, "Could not fetch FCM token")
                onFinished?.invoke()
            }
    }

    fun registerToken(
        context: Context,
        token: String,
        reason: String,
        force: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            onFinished?.invoke()
            return
        }
        val appContext = context.applicationContext
        val entryPoint = entryPoint(appContext)
        val settings = entryPoint.settingsRepository()
        if (settings.sollServerUrl.isBlank()) {
            if (onFinished != null) {
                settings.sollPushTokenLastError = "Soll server URL is blank"
            }
            onFinished?.invoke()
            return
        }
        if (!force && !settings.shouldRegisterSollPushToken(cleanToken)) {
            onFinished?.invoke()
            return
        }

        scope.launch {
            try {
                val result = entryPoint.sollGateway().registerAndroidPushToken(cleanToken, provider = "fcm")
                result
                    .onSuccess { registration ->
                        if (registration.success) {
                            settings.markSollPushTokenRegistered(cleanToken)
                            Timber.i(
                                "Registered FCM token with Soll server, reason=%s enabled=%s tokenCount=%d",
                                reason,
                                registration.enabled,
                                registration.tokenCount,
                            )
                        } else {
                            settings.sollPushTokenLastError = registration.reason ?: "registration_failed"
                        }
                    }
                    .onFailure { error ->
                        settings.sollPushTokenLastError = error.message ?: error::class.java.simpleName
                        Timber.w(error, "Could not register FCM token with Soll server")
                    }
            } finally {
                onFinished?.invoke()
            }
        }
    }

    private fun entryPoint(context: Context): AndroidPushTokenRegistrationEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AndroidPushTokenRegistrationEntryPoint::class.java,
        )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AndroidPushTokenRegistrationEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun sollGateway(): SollGateway
}
