package com.example.safecopy.data.auth

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** То же, что unit-тест, но с настоящим Android Keystore и SharedPreferences. */
@RunWith(AndroidJUnit4::class)
class KeystorePinRepositoryInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val keys = AndroidKeystoreKeys()

    private fun newRepository() = KeystorePinRepository(SharedPreferencesBlobStore(prefs), KeystoreDeviceWrapper(keys))

    @Before
    @After
    fun cleanUp() {
        prefs.edit().clear().commit()
        keys.delete(AndroidKeystoreKeys.DEVICE_KEY_ALIAS)
    }

    @Test
    fun pinRoundTripWithRealKeystore() = runBlocking {
        val created = newRepository().createPin("4821").copyBytes()

        val unlocked = newRepository().unlockWithPin("4821")
        assertNotNull(unlocked)
        assertArrayEquals(created, unlocked!!.copyBytes())
        assertNull(newRepository().unlockWithPin("4822"))
    }

    @Test(expected = com.example.safecopy.domain.auth.VaultUnavailableException::class)
    fun deletedKeystoreKeyMakesVaultUnavailable(): Unit = runBlocking {
        newRepository().createPin("4821")
        keys.delete(AndroidKeystoreKeys.DEVICE_KEY_ALIAS)
        newRepository().unlockWithPin("4821")
    }

    private companion object {
        const val PREFS = "test_keystore_pin"
    }
}
