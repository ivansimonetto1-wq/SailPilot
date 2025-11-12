package com.perseitech.sailpilot.ports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class PortInfo(
    val title: String,
    val country: String?,
    val UNLOCODE: String?,
    val description: String?,
    val website: String?,
    val phone: String?,
    val email: String?,
    val address: String?
)

object PortInfoService {
    private val client by lazy { OkHttpClient() }

    /**
     * Cerca info porto usando SeaRates World Sea Ports.
     * Richiede un apiKey (Bearer o api_key query a seconda del prodotto).
     * Implementazione generica: prova endpoint documentato; se 401/404 -> null.
     *
     * Docs: World Sea Ports API. (https://docs.searates.com/reference/world-sea-ports/introduction)
     */
    suspend fun fetchPortInfo(apiKey: String?, query: String): PortInfo? = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) return@withContext null

        // Esempio di endpoint generico: molti prodotti SeaRates usano ?api_key=
        // Qui simulo una ricerca per nome/UNLOCODE. Adatta all'endpoint specifico del tuo piano.
        val url = "https://api.searates.com/world-sea-ports/v1/ports?search=${query}&api_key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        runCatching {
            client.newCall(req).execute().use { rsp ->
                if (!rsp.isSuccessful) return@use null
                val body = rsp.body?.string() ?: return@use null
                val js = JSONObject(body)
                val arr = js.optJSONArray("results") ?: js.optJSONArray("data") ?: return@use null
                if (arr.length() == 0) return@use null
                val p = arr.getJSONObject(0)
                PortInfo(
                    title = p.optString("name", query),
                    country = p.optString("country", null),
                    UNLOCODE = p.optString("unlocode", null),
                    description = p.optString("description", null),
                    website = p.optString("website", null),
                    phone = p.optString("phone", null),
                    email = p.optString("email", null),
                    address = p.optString("address", null)
                )
            }
        }.getOrNull()
    }
}
