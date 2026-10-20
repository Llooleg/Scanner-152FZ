package com.example.safecopy.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.safecopy.R
import com.example.safecopy.ui.theme.SafecopyTheme

@Composable
fun LockRouteScreen(
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isUnlocked) {
        if (state.isUnlocked) onUnlocked()
    }
    LockScreen(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        // TODO(этап 1): BiometricPrompt + CryptoObject, кнопка видна только если биометрия включена и доступна.
        onBiometric = null,
    )
}

@Composable
fun LockScreen(
    state: LockUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    when (state.mode) {
                        LockMode.CreatePin, LockMode.Loading -> R.string.lock_create_title
                        LockMode.ConfirmPin -> R.string.lock_confirm_title
                        LockMode.EnterPin -> R.string.lock_enter_title
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            PinDots(filled = state.enteredDigits, total = LockViewModel.PIN_LENGTH)
            Box(modifier = Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                val message = when {
                    state.lockoutSecondsLeft > 0 ->
                        stringResource(R.string.lock_locked_out, state.lockoutSecondsLeft)
                    state.error == LockError.Mismatch -> stringResource(R.string.lock_error_mismatch)
                    state.error == LockError.WrongPin -> stringResource(R.string.lock_error_wrong)
                    else -> null
                }
                if (message != null) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            PinPad(enabled = state.isInputEnabled, onDigit = onDigit, onBackspace = onBackspace)
            Spacer(Modifier.height(16.dp))
            if (onBiometric != null && state.mode == LockMode.EnterPin) {
                TextButton(onClick = onBiometric, enabled = state.isInputEnabled) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.lock_biometric))
                }
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { index ->
            val color = if (index < filled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (Int) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> PinKey(enabled, onClick = { onDigit(digit) }) { Text("$digit") } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.size(KeySize))
            PinKey(enabled, onClick = { onDigit(0) }) { Text("0") }
            PinKey(enabled, onClick = onBackspace, filled = false) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = stringResource(R.string.lock_backspace),
                )
            }
        }
    }
}

private val KeySize = 72.dp

@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    filled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineSmall) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
    SafecopyTheme {
        LockScreen(
            state = LockUiState(mode = LockMode.EnterPin, enteredDigits = 2),
            onDigit = {},
            onBackspace = {},
            onBiometric = {},
        )
    }
}
