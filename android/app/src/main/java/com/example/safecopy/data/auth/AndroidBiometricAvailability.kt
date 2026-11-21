package com.example.safecopy.data.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import com.example.safecopy.domain.auth.BiometricAvailability

class AndroidBiometricAvailability(private val context: Context) : BiometricAvailability {
    override fun canUseStrongBiometric(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
}
