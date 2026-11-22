package com.example.safecopy.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LaunchRoute

@Serializable
data object LockRoute

// Вкладки нижней навигации
@Serializable
data object DocumentsRoute

@Serializable
data object JournalRoute

@Serializable
data object SettingsRoute

/** Съёмка нового документа. После сохранения в хранилище открывается конвейер копии. */
@Serializable
data object ScanRoute

/** Вложенный граф конвейера создания копии для сохранённого документа. */
@Serializable
data class CopyFlowRoute(val documentId: Long)

@Serializable
data object MaskRoute

@Serializable
data object WatermarkRoute

@Serializable
data object ExportRoute
