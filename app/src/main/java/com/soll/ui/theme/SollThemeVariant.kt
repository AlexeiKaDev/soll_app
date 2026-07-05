package com.soll.ui.theme

enum class SollThemeVariant(
    val storageKey: String,
    val title: String,
    val description: String,
) {
    SOLL(
        storageKey = "soll",
        title = "Soll",
        description = "Светлая teal-тема Soll для мобильного приложения",
    ),
    CLASSIC(
        storageKey = "classic",
        title = "Классика",
        description = "Текущая темная тема Soll",
    ),
    AURORA(
        storageKey = "aurora",
        title = "Аврора",
        description = "Темная тема с зеленым, янтарным и синим акцентами",
    ),
    AQUIK(
        storageKey = "aquik",
        title = "Aquik",
        description = "Темная аква-тема из Aquik Controller",
    );

    companion object {
        val default: SollThemeVariant = SOLL

        fun fromStorage(value: String?): SollThemeVariant =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}
