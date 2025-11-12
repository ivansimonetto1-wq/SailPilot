package com.perseitech.sailpilot.settings

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "sailpilot_settings")

/**
 * Persistenza connessioni in DataStore (JSON).
 * Supporta più connessioni NMEA 0183, Signal K e “NMEA 2000 (via gateway)”.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_CONNECTIONS = stringPreferencesKey("connections_json")
    }

    data class Connection(
        val id: String,
        val kind: Kind,
        val name: String,

        // --- per NMEA 2000 (via gateway) ---
        val bridge: Bridge? = null, // come viene bridgiato N2K: SIGNALK o NMEA0183

        // --- campi per NMEA 0183 (anche quando kind=NMEA2000 e bridge=NMEA0183) ---
        val host: String? = null,
        val port: Int? = null,
        val protocol: NmeaProtocol? = null,

        // --- campi per Signal K (anche quando kind=NMEA2000 e bridge=SIGNALK) ---
        val wsUrl: String? = null,
        val token: String? = null
    ) {
        enum class Kind { NMEA0183, SIGNALK, NMEA2000 }
        enum class NmeaProtocol { TCP, UDP }
        enum class Bridge { SIGNALK, NMEA0183 }
    }

    fun connections(): Flow<List<Connection>> {
        return context.dataStore.data.map { prefs ->
            val js = prefs[KEY_CONNECTIONS] ?: "[]" // default vuoto
            parseConnections(js)
        }
    }

    suspend fun saveConnections(list: List<Connection>) {
        val js = serializeConnections(list)
        context.dataStore.edit { it[KEY_CONNECTIONS] = js }
    }

    private fun parseConnections(json: String): List<Connection> {
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Connection(
                            id   = o.getString("id"),
                            kind = Connection.Kind.valueOf(o.getString("kind")),
                            name = o.optString("name", o.getString("id")),

                            bridge = o.optString("bridge", "").takeIf { it.isNotEmpty() }?.let {
                                Connection.Bridge.valueOf(it)
                            },

                            host = o.optString("host", null),
                            port = o.optInt("port", -1).takeIf { it > 0 },
                            protocol = o.optString("protocol", "").takeIf { it.isNotEmpty() }?.let {
                                Connection.NmeaProtocol.valueOf(it)
                            },

                            wsUrl = o.optString("wsUrl", null),
                            token = o.optString("token", null).takeIf { it.isNotEmpty() }
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun serializeConnections(list: List<Connection>): String {
        val arr = JSONArray()
        list.forEach { c ->
            val o = JSONObject()
            o.put("id", c.id)
            o.put("kind", c.kind.name)
            o.put("name", c.name)

            c.bridge?.let { o.put("bridge", it.name) }

            c.host?.let { o.put("host", it) }
            c.port?.let { o.put("port", it) }
            c.protocol?.let { o.put("protocol", it.name) }

            c.wsUrl?.let { o.put("wsUrl", it) }
            c.token?.let { o.put("token", it) }

            arr.put(o)
        }
        return arr.toString()
    }
}
