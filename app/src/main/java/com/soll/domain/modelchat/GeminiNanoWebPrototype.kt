package com.soll.domain.modelchat

/**
 * Availability reported by a web host after feature-detecting its built-in AI API.
 * The Android app must not assume that an Android WebView exposes that API.
 */
enum class WebAiAvailability {
    READY,
    DOWNLOADABLE,
    UNAVAILABLE,
    UNKNOWN,
}

enum class WebAiCapability {
    PROMPT,
    SUMMARIZE,
    REWRITE,
}

data class WebAiCapabilitySnapshot(
    val prompt: WebAiAvailability = WebAiAvailability.UNKNOWN,
    val summarize: WebAiAvailability = WebAiAvailability.UNKNOWN,
    val rewrite: WebAiAvailability = WebAiAvailability.UNKNOWN,
) {
    fun availabilityFor(capability: WebAiCapability): WebAiAvailability =
        when (capability) {
            WebAiCapability.PROMPT -> prompt
            WebAiCapability.SUMMARIZE -> summarize
            WebAiCapability.REWRITE -> rewrite
        }
}

enum class WebAiPrototypeRoute {
    ON_DEVICE,
    ON_DEVICE_AFTER_DOWNLOAD,
    DOWNLOAD_CONSENT_REQUIRED,
    SERVER_FALLBACK,
    BLOCKED_PRIVATE_REQUEST,
}

enum class WebAiPrototypeReason {
    CAPABILITY_READY,
    EXPLICIT_DOWNLOAD_ALLOWED,
    MODEL_DOWNLOAD_REQUIRES_CONSENT,
    LOCAL_CAPABILITY_UNAVAILABLE,
    PRIVATE_CONTENT_REQUIRES_LOCAL_CAPABILITY,
}

data class WebAiPrototypeDecision(
    val route: WebAiPrototypeRoute,
    val reason: WebAiPrototypeReason,
    val serverRequest: ModelChatRequest? = null,
) {
    init {
        require((route == WebAiPrototypeRoute.SERVER_FALLBACK) == (serverRequest != null)) {
            "Only the server fallback route may expose a server request"
        }
    }
}

/**
 * Dependency-free integration seam for a future Soll web host backed by browser built-in AI.
 *
 * A host probes the requested capability and supplies [WebAiCapabilitySnapshot]. This router
 * keeps the current backend-mediated model chat as a non-private fallback, never downloads a
 * model without explicit consent, and never silently sends a private turn to the server.
 */
object GeminiNanoWebPrototype {
    fun plan(
        request: ModelChatRequest,
        capability: WebAiCapability,
        snapshot: WebAiCapabilitySnapshot,
        allowModelDownload: Boolean = false,
    ): WebAiPrototypeDecision {
        return when (snapshot.availabilityFor(capability)) {
            WebAiAvailability.READY -> WebAiPrototypeDecision(
                route = WebAiPrototypeRoute.ON_DEVICE,
                reason = WebAiPrototypeReason.CAPABILITY_READY,
            )

            WebAiAvailability.DOWNLOADABLE -> {
                if (allowModelDownload) {
                    WebAiPrototypeDecision(
                        route = WebAiPrototypeRoute.ON_DEVICE_AFTER_DOWNLOAD,
                        reason = WebAiPrototypeReason.EXPLICIT_DOWNLOAD_ALLOWED,
                    )
                } else {
                    WebAiPrototypeDecision(
                        route = WebAiPrototypeRoute.DOWNLOAD_CONSENT_REQUIRED,
                        reason = WebAiPrototypeReason.MODEL_DOWNLOAD_REQUIRES_CONSENT,
                    )
                }
            }

            WebAiAvailability.UNAVAILABLE,
            WebAiAvailability.UNKNOWN,
            -> unavailableDecision(request)
        }
    }

    private fun unavailableDecision(request: ModelChatRequest): WebAiPrototypeDecision {
        if (request.messages.any { it.private }) {
            return WebAiPrototypeDecision(
                route = WebAiPrototypeRoute.BLOCKED_PRIVATE_REQUEST,
                reason = WebAiPrototypeReason.PRIVATE_CONTENT_REQUIRES_LOCAL_CAPABILITY,
            )
        }

        return WebAiPrototypeDecision(
            route = WebAiPrototypeRoute.SERVER_FALLBACK,
            reason = WebAiPrototypeReason.LOCAL_CAPABILITY_UNAVAILABLE,
            serverRequest = request.safeForServer(),
        )
    }
}
