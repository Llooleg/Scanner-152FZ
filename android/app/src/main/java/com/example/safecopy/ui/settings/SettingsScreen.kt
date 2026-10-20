package com.example.safecopy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.safecopy.R
import com.example.safecopy.ui.theme.SafecopyTheme

@Composable
fun SettingsRouteScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBiometricChange = viewModel::setBiometricEnabled,
        onBlockScreenshotsChange = viewModel::setBlockScreenshots,
        onHideInRecentsChange = viewModel::setHideInRecents,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBiometricChange: (Boolean) -> Unit,
    onBlockScreenshotsChange: (Boolean) -> Unit,
    onHideInRecentsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_section_security).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            SwitchRow(
                title = stringResource(R.string.settings_biometric),
                summary = stringResource(R.string.settings_biometric_summary),
                checked = state.biometricEnabled,
                onCheckedChange = onBiometricChange,
            )
            NavRow(
                title = stringResource(R.string.settings_autolock),
                summary = stringResource(R.string.settings_autolock_summary, state.autoLockMinutes),
                onClick = { /* TODO(этап 8): выбор интервала */ },
            )
            SwitchRow(
                title = stringResource(R.string.settings_block_screenshots),
                summary = stringResource(R.string.settings_block_screenshots_summary),
                checked = state.blockScreenshots,
                onCheckedChange = onBlockScreenshotsChange,
            )
            SwitchRow(
                title = stringResource(R.string.settings_hide_recents),
                summary = stringResource(R.string.settings_hide_recents_summary),
                checked = state.hideInRecents,
                onCheckedChange = onHideInRecentsChange,
            )
            NavRow(
                title = stringResource(R.string.settings_watermark_template),
                summary = stringResource(R.string.settings_watermark_template_summary),
                onClick = { /* TODO(этап 8) */ },
            )
            NavRow(
                title = stringResource(R.string.settings_backup),
                summary = stringResource(R.string.settings_backup_summary),
                onClick = { /* TODO(этап 8): SAF ACTION_CREATE_DOCUMENT */ },
            )
            NavRow(
                title = stringResource(R.string.settings_wipe),
                summary = stringResource(R.string.settings_wipe_summary),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { /* TODO(этап 8): диалог подтверждения, удаление файлов, БД и ключей Keystore */ },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun NavRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    titleColor: Color = Color.Unspecified,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
        colors = if (titleColor != Color.Unspecified) {
            ListItemDefaults.colors(headlineColor = titleColor)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SafecopyTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onBiometricChange = {},
            onBlockScreenshotsChange = {},
            onHideInRecentsChange = {},
        )
    }
}
