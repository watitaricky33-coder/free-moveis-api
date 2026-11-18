package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.min
import kotlin.math.pow

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "tmdb_embed_auth")

class AuthRepository(private val context: Context) {
    companion object {
        const val DEFAULT_USERNAME = "admin"
        const val DEFAULT_PASSWORD = "change-me"
        const val MAX_ATTEMPTS_WINDOW = 5
        const val BASE_LOCK_MS = 5 * 60 * 1000L
        const val MAX_LOCK_MULTIPLIER = 8
        const val SESSION_TTL_MS = 12 * 60 * 60 * 1000L

        private val KEY_USERNAME = stringPreferencesKey("auth_username")
        private val KEY_PASSWORD_HASH = stringPreferencesKey("auth_password_hash")
        private val KEY_IS_DEFAULT_PW = stringPreferencesKey("auth_is_default_pw")
        private val KEY_FAILED_COUNT = intPreferencesKey("auth_failed_count")
        private val KEY_LOCKED_UNTIL = longPreferencesKey("auth_locked_until")
        private val KEY_SESSION_EXP = longPreferencesKey("auth_session_exp")

        fun hashPassword(password: String, saltHex: String? = null): String {
            val salt = saltHex ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), 10000, 256)
            val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
            return "$salt:$derived"
        }

        fun verifyPassword(password: String, stored: String?): Boolean {
            if (stored.isNullOrBlank() || !stored.contains(':')) return false
            val parts = stored.split(':', limit = 2)
            val candidate = hashPassword(password, parts[0])
            return MessageDigest.isEqual(candidate.toByteArray(), stored.toByteArray())
        }
    }

    val authStateFlow: Flow<AuthState> = context.authDataStore.data.map { prefs ->
        val now = System.currentTimeMillis()
        val exp = prefs[KEY_SESSION_EXP] ?: 0L
        val lockedUntil = prefs[KEY_LOCKED_UNTIL] ?: 0L
        val failed = prefs[KEY_FAILED_COUNT] ?: 0
        AuthState(
            isAuthenticated = exp > now,
            username = prefs[KEY_USERNAME] ?: DEFAULT_USERNAME,
            remainingAttempts = (MAX_ATTEMPTS_WINDOW - failed).coerceAtLeast(0),
            lockedUntilMs = if (lockedUntil > now) lockedUntil else 0L,
            sessionExpiresAtMs = exp,
            isUsingDefaultPassword = (prefs[KEY_IS_DEFAULT_PW] ?: "true") == "true"
        )
    }

    suspend fun ensureSeeded() {
        val prefs = context.authDataStore.data.first()
        if (prefs[KEY_PASSWORD_HASH].isNullOrBlank()) {
            context.authDataStore.edit { editPrefs ->
                editPrefs[KEY_USERNAME] = DEFAULT_USERNAME
                editPrefs[KEY_PASSWORD_HASH] = hashPassword(DEFAULT_PASSWORD)
                editPrefs[KEY_IS_DEFAULT_PW] = "true"
            }
        }
    }

    sealed class LoginResult {
        data object Success : LoginResult()
        data class InvalidCredentials(val remainingAttempts: Int) : LoginResult()
        data class Locked(val retryAfterSeconds: Long) : LoginResult()
    }

    suspend fun login(username: String, password: String): LoginResult {
        ensureSeeded()
        val prefs = context.authDataStore.data.first()
        val now = System.currentTimeMillis()
        val lockedUntil = prefs[KEY_LOCKED_UNTIL] ?: 0L
        if (lockedUntil > now) {
            return LoginResult.Locked(((lockedUntil - now) / 1000L).coerceAtLeast(1L))
        }

        val storedUser = prefs[KEY_USERNAME] ?: DEFAULT_USERNAME
        val storedHash = prefs[KEY_PASSWORD_HASH]

        if (username.trim() == storedUser && verifyPassword(password, storedHash)) {
            context.authDataStore.edit { e ->
                e[KEY_FAILED_COUNT] = 0
                e[KEY_LOCKED_UNTIL] = 0L
                e[KEY_SESSION_EXP] = now + SESSION_TTL_MS
            }
            return LoginResult.Success
        }

        val nextCount = (prefs[KEY_FAILED_COUNT] ?: 0) + 1
        var nextLockUntil = 0L
        if (nextCount > MAX_ATTEMPTS_WINDOW) {
            val over = nextCount - MAX_ATTEMPTS_WINDOW
            val mult = min(MAX_LOCK_MULTIPLIER.toDouble(), 2.0.pow((over - 1).toDouble())).toLong()
            nextLockUntil = now + BASE_LOCK_MS * mult
        }
        context.authDataStore.edit { e ->
            e[KEY_FAILED_COUNT] = nextCount
            e[KEY_LOCKED_UNTIL] = nextLockUntil
        }

        return if (nextLockUntil > now) {
            LoginResult.Locked(((nextLockUntil - now) / 1000L).coerceAtLeast(1L))
        } else {
            LoginResult.InvalidCredentials((MAX_ATTEMPTS_WINDOW - nextCount).coerceAtLeast(0))
        }
    }

    suspend fun logout() {
        context.authDataStore.edit { e ->
            e[KEY_SESSION_EXP] = 0L
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        ensureSeeded()
        if (newPassword.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters"))
        }
        val prefs = context.authDataStore.data.first()
        val storedHash = prefs[KEY_PASSWORD_HASH]
        if (!verifyPassword(oldPassword, storedHash)) {
            return Result.failure(IllegalStateException("Invalid old password"))
        }
        context.authDataStore.edit { e ->
            e[KEY_PASSWORD_HASH] = hashPassword(newPassword)
            e[KEY_IS_DEFAULT_PW] = "false"
        }
        return Result.success(Unit)
    }
}
