package com.perseitech.sailpilot.ports

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class Port(
    val name: String,
    val country: String,
    val unlocode: String?,
    val lat: Double,
    val lon: Double
)

object PortsRepository {
    suspend fun loadPorts(ctx: Context): List<Port> = withContext(Dispatchers.IO) {
        runCatching {
            ctx.assets.open("ports.json").bufferedReader().use { it.readText() }
        }.mapCatching { json ->
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Port(
                            name = o.getString("name"),
                            country = o.optString("country", ""),
                            unlocode = o.optString("unlocode", null),
                            lat = o.getDouble("lat"),
                            lon = o.getDouble("lon")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }
}
