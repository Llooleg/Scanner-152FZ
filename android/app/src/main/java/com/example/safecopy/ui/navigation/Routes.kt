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

// Вложенный граф конвейера создания копии
@Serializable
data object CopyFlowRoute

@Serializable
data object ScanRoute

@Serializable
data object MaskRoute

@Serializable
data object WatermarkRoute

@Serializable
data object ExportRoute
