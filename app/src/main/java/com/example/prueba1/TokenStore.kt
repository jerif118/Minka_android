package com.minka.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.android.identity.util.UUID

object SecureStore {
    private const val PREF = "secure_prefs"
    private const val KEY_TOKEN   = "jwt"
    private const val KEY_CLIENT  = "client_id"

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx,
        PREF,
        MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(ctx: Context, token: String) =
        prefs(ctx).edit().putString(KEY_TOKEN, token).apply()

    fun loadToken(ctx: Context): String? =
        prefs(ctx).getString(KEY_TOKEN, null)

    fun clearToken(ctx: Context) =
        prefs(ctx).edit().remove(KEY_TOKEN).apply()

    // (opcional) persistir un clientId único por dispositivo
    fun getOrCreateClientId(ctx: Context): String =
        prefs(ctx).getString(KEY_CLIENT, null)
            ?: UUID.randomUUID().toString().also {
                prefs(ctx).edit().putString(KEY_CLIENT, it).apply()
            }
}