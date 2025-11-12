package com.perseitech.sailpilot.regatta

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.Locale

// ---------- DataStore ----------
private val Context.regattaStore by preferencesDataStore("regatta_settings")

// ---------- Modelli ----------
data class BoatClass(
    val id: String,
    val name: String,
    val loa: Double? = null,
    val beam: Double? = null,
    val draft: Double? = null,
    val rig: String? = null
)

enum class HullType { MONOHULL, CATAMARAN, TRIMARAN }
enum class SailMaterial { DACRON, LAMINATE, MEMBRANE, _3DL, OTHER }

data class RegattaSettings(
    val classId: String? = null,
    val hull: HullType = HullType.MONOHULL,
    val sailMaterial: SailMaterial = SailMaterial.DACRON,
    val inventory: Set<String> = emptySet()
)

// ---------- Repo classi ----------
object BoatClassesRepo {

    // Cache (file privato app)
    private const val CACHE_FILE = "boat_classes_cache.json"
    private const val CACHE_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000 // 7 giorni

    /**
     * Carica classi:
     * 1) JSON locale (assets/boat_classes.json)
     * 2) Cache disco se valida
     * 3) Wikipedia IT + EN (merge + dedup), poi salva cache
     * 4) Se rete KO ma c'è cache vecchia, usa quella
     */
    suspend fun load(ctx: Context): List<BoatClass> = withContext(Dispatchers.IO) {
        val local = loadLocal(ctx)

        // cache
        val cacheFile = File(ctx.filesDir, CACHE_FILE)
        readCache(cacheFile)?.let { (ts, cached) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL_MS) {
                return@withContext mergeDistinct(local, cached)
                    .sortedBy { it.name.lowercase(Locale.ROOT) }
            }
        }

        // rete
        val itList = runCatching { loadFromWikipedia("it", "Categoria:Classi_veliche") }.getOrElse { emptyList() }
        val enList = runCatching { loadFromWikipedia("en", "Category:Sailing classes") }.getOrElse { emptyList() }
        val merged = mergeDistinct(local, itList + enList).sortedBy { it.name.lowercase(Locale.ROOT) }

        // salva cache best-effort
        runCatching { writeCache(cacheFile, merged) }

        // fallback cache scaduta se rete vuota
        if (merged.isEmpty()) {
            readCache(cacheFile)?.second?.let { stale ->
                return@withContext mergeDistinct(local, stale)
                    .sortedBy { it.name.lowercase(Locale.ROOT) }
            }
        }
        merged
    }

    private fun loadLocal(ctx: Context): List<BoatClass> = runCatching {
        ctx.assets.open("boat_classes.json").bufferedReader().use { it.readText() }
    }.mapCatching { js ->
        val arr = JSONArray(js)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    BoatClass(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        loa = o.optDouble("loa", Double.NaN).takeIf { it == it },
                        beam = o.optDouble("beam", Double.NaN).takeIf { it == it },
                        draft = o.optDouble("draft", Double.NaN).takeIf { it == it },
                        rig = o.optString("rig", null)
                    )
                )
            }
        }
    }.getOrElse { emptyList() }

    /** Wikipedia API (paginato) */
    private fun loadFromWikipedia(lang: String, categoryTitle: String): List<BoatClass> {
        val base =
            "https://$lang.wikipedia.org/w/api.php?action=query&list=categorymembers&format=json&cmlimit=500&cmtitle=" +
                    URLEncoder.encode(categoryTitle, "UTF-8")
        var cont: String? = null
        val out = mutableListOf<BoatClass>()
        do {
            val url = if (cont == null) base else "$base&cmcontinue=${URLEncoder.encode(cont, "UTF-8")}"
            val conn = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
            }
            conn.inputStream.bufferedReader().use { br ->
                val text = br.readText()
                val obj = JSONObject(text)
                val query = obj.optJSONObject("query")
                val members = query?.optJSONArray("categorymembers") ?: JSONArray()
                for (i in 0 until members.length()) {
                    val t = members.getJSONObject(i).optString("title")
                    if (t.isNullOrBlank()) continue
                    val id = t.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    out += BoatClass(id = "${lang}_wiki_$id", name = t)
                }
                cont = obj.optJSONObject("continue")?.optString("cmcontinue")
            }
            conn.disconnect()
        } while (!cont.isNullOrBlank())
        return out
    }

    private fun mergeDistinct(a: List<BoatClass>, b: List<BoatClass>): List<BoatClass> {
        val seen = hashSetOf<String>()
        val out = ArrayList<BoatClass>(a.size + b.size)
        (a + b).forEach { bc ->
            val key = bc.name.lowercase(Locale.ROOT).trim()
            if (seen.add(key)) out += bc
        }
        return out
    }

    // ------ cache on-disk ------
    private fun writeCache(file: File, list: List<BoatClass>) {
        val root = JSONObject()
        root.put("ts", System.currentTimeMillis())
        val arr = JSONArray()
        list.forEach { bc ->
            val o = JSONObject()
            o.put("id", bc.id)
            o.put("name", bc.name)
            if (bc.loa != null) o.put("loa", bc.loa) else o.put("loa", JSONObject.NULL)
            if (bc.beam != null) o.put("beam", bc.beam) else o.put("beam", JSONObject.NULL)
            if (bc.draft != null) o.put("draft", bc.draft) else o.put("draft", JSONObject.NULL)
            if (bc.rig != null) o.put("rig", bc.rig) else o.put("rig", JSONObject.NULL)
            arr.put(o)
        }
        root.put("classes", arr)
        file.writeText(root.toString())
    }

    /** ritorna Pair<timestamp, list> oppure null */
    private fun readCache(file: File): Pair<Long, List<BoatClass>>? = runCatching {
        if (!file.isFile) return null
        val text = file.readText()
        val obj = JSONObject(text)
        val ts = obj.optLong("ts", 0L)
        val arr = obj.optJSONArray("classes") ?: JSONArray()
        val list = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    BoatClass(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        loa = o.optDouble("loa", Double.NaN).takeIf { it == it },
                        beam = o.optDouble("beam", Double.NaN).takeIf { it == it },
                        draft = o.optDouble("draft", Double.NaN).takeIf { it == it },
                        rig = o.optString("rig", null)
                    )
                )
            }
        }
        ts to list
    }.getOrNull()
}

// ---------- Repo impostazioni regata ----------
class RegattaSettingsRepo(private val ctx: Context) {
    private val CLASS = stringPreferencesKey("rg_class")
    private val HULL = stringPreferencesKey("rg_hull")
    private val MAT  = stringPreferencesKey("rg_mat")
    private val INV  = stringPreferencesKey("rg_inv") // CSV '|'

    val flow: Flow<RegattaSettings> = ctx.regattaStore.data.map { p ->
        RegattaSettings(
            classId = p[CLASS].orEmpty().ifBlank { null },
            hull = p[HULL]?.let { runCatching { HullType.valueOf(it) }.getOrNull() } ?: HullType.MONOHULL,
            sailMaterial = p[MAT]?.let { runCatching { SailMaterial.valueOf(it) }.getOrNull() } ?: SailMaterial.DACRON,
            inventory = p[INV]?.split('|')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        )
    }

    suspend fun save(s: RegattaSettings) {
        ctx.regattaStore.edit { e ->
            e[CLASS] = s.classId ?: ""
            e[HULL] = s.hull.name
            e[MAT]  = s.sailMaterial.name
            e[INV]  = s.inventory.joinToString("|")
        }
    }
}
