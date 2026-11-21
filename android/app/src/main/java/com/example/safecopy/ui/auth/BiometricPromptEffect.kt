package com.example.safecopy.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.safecopy.R
import javax.crypto.Cipher

enum class BiometricPurpose { Unlock, Enroll }

/**
 * Запрос ViewModel на показ BiometricPrompt. Обычный класс, а не data class:
 * два запроса с одним и тем же cipher — всё равно два разных запроса.
 */
class BiometricRequest(val cipher: Cipher, val purpose: BiometricPurpose)

/**
 * Показывает системный BiometricPrompt (класс Strong) с CryptoObject, когда [request] не null.
 * [onError] получает true, если пользователь сам отказался (кнопка «Использовать PIN-код», отмена).
 */
@Composable
fun BiometricPromptEffect(
    request: BiometricRequest?,
    onAuthenticated: (Cipher) -> Unit,
    onError: (cancelledByUser: Boolean) -> Unit,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)
    val currentOnError by rememberUpdatedState(onError)

    val unlockTitle = stringResource(R.string.biometric_unlock_title)
    val unlockSubtitle = stringResource(R.string.biometric_unlock_subtitle)
    val unlockNegative = stringResource(R.string.biometric_unlock_negative)
    val enrollTitle = stringResource(R.string.biometric_enroll_title)
    val enrollSubtitle = stringResource(R.string.biometric_enroll_subtitle)
    val enrollNegative = stringResource(R.string.biometric_enroll_negative)

    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        if (activity == null) {
            currentOnError(false)
            return@LaunchedEffect
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                if (cipher != null) currentOnAuthenticated(cipher) else currentOnError(false)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                currentOnError(errorCode in CANCEL_CODES)
            }
            // onAuthenticationFailed — палец не тот, окно остаётся открытым: ничего не делаем.
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .apply {
                when (request.purpose) {
                    BiometricPurpose.Unlock -> setTitle(unlockTitle).setSubtitle(unlockSubtitle)
                        .setNegativeButtonText(unlockNegative)

                    BiometricPurpose.Enroll -> setTitle(enrollTitle).setSubtitle(enrollSubtitle)
                        .setNegativeButtonText(enrollNegative)
                }
            }
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(request.cipher))
    }
}

private val CANCEL_CODES = setOf(
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_CANCELED,
)
