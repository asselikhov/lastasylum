package com.lastasylum.alliance.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    @Volatile
    private var memoryAccessToken: String? = null

    @Volatile
    private var memoryRefreshToken: String? = null

    private fun createPrefs(appContext: Context): SharedPreferences {
        return runCatching {
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME_SECURE,
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse { e ->
            Log.e(TAG, "EncryptedSharedPreferences unavailable; using plain prefs fallback", e)
            appContext.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        memoryAccessToken = accessToken
        memoryRefreshToken = refreshToken
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    fun clearTokens() {
        memoryAccessToken = null
        memoryRefreshToken = null
        prefs.edit().clear().apply()
    }

    fun getAccessToken(): String? {
        memoryAccessToken?.let { return it }
        return prefs.getString(KEY_ACCESS, null)?.also { memoryAccessToken = it }
    }

    fun getRefreshToken(): String? {
        memoryRefreshToken?.let { return it }
        return prefs.getString(KEY_REFRESH, null)?.also { memoryRefreshToken = it }
    }

    private companion object {
        private const val TAG = "TokenStore"
        private const val PREFS_NAME_SECURE = "last_asylum_secure_tokens"
        private const val PREFS_NAME_FALLBACK = "last_asylum_tokens_fallback"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
    }
}
