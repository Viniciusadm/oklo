package com.archieapps.oklo.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.archieapps.oklo.core.net.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("oklo-session")

data class Session(
    val token: String?,
    val name: String?,
    val email: String?,
    val blocked: Int,
) {
    val signedIn: Boolean
        get() = !token.isNullOrBlank()
}

class SessionStore(context: Context) : TokenProvider {
    private val store = context.sessionDataStore

    @Volatile
    private var cachedToken: String? = null

    val session: Flow<Session> = store.data.map { prefs ->
        val token = prefs[TOKEN]
        cachedToken = token
        Session(
            token = token,
            name = prefs[NAME],
            email = prefs[EMAIL],
            blocked = prefs[BLOCKED] ?: 0,
        )
    }

    override fun currentToken(): String? = cachedToken

    suspend fun load(): Session = session.first()

    suspend fun save(token: String, name: String?, email: String?, blocked: Int) {
        cachedToken = token
        store.edit {
            it[TOKEN] = token
            if (name != null) it[NAME] = name
            if (email != null) it[EMAIL] = email
            it[BLOCKED] = blocked
        }
    }

    suspend fun setBlocked(value: Int) {
        store.edit { it[BLOCKED] = value }
    }

    suspend fun clear() {
        cachedToken = null
        store.edit { it.clear() }
    }

    companion object {
        private val TOKEN = stringPreferencesKey("token")
        private val NAME = stringPreferencesKey("name")
        private val EMAIL = stringPreferencesKey("email")
        private val BLOCKED = intPreferencesKey("blocked")
    }
}
