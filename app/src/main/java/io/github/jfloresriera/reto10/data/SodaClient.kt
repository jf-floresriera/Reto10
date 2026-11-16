package io.github.jfloresriera.reto10.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : Exception(message)

data class ApiConfig(val token: String, val mode: ApiMode) {
    val useV3: Boolean
        get() = when (mode) {
            ApiMode.V3 -> true
            ApiMode.V2 -> false
            ApiMode.AUTO -> token.isNotBlank()
        }
}

/**
 * Cliente de la API SODA de datos.gov.co.
 * - v3: POST /api/v3/views/{id}/query.json con el token en el encabezado X-App-Token.
 * - v2: GET /resource/{id}.json?$query=... (sirve sin token, con límite de peticiones).
 * Todo el JSON se procesa con org.json (JSONObject y JSONArray) del SDK de Android.
 */
class SodaClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private companion object {
        const val HOST = "https://www.datos.gov.co"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        val DATASET_ID = Regex("^[a-z0-9]{4}-[a-z0-9]{4}$")
    }

    private fun checkId(datasetId: String): String {
        val id = datasetId.trim().lowercase()
        require(DATASET_ID.matches(id)) { "El identificador debe tener el formato xxxx-xxxx (por ejemplo 2pnw-mmge)." }
        return id
    }

    private fun builder(cfg: ApiConfig): Request.Builder =
        Request.Builder().header("Accept", "application/json").apply {
            if (cfg.token.isNotBlank()) header("X-App-Token", cfg.token)
        }

    private fun execute(request: Request): String {
        return try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(describe(resp.code, text), resp.code)
                text
            }
        } catch (e: IOException) {
            throw ApiException("No se pudo conectar con datos.gov.co. Revisa tu conexión a internet. (${e.javaClass.simpleName})")
        }
    }

    private fun describe(code: Int, body: String): String = when (code) {
        401, 403 -> "datos.gov.co rechazó la petición (HTTP $code). Revisa el app token en Ajustes."
        404 -> "No se encontró el dataset o el servicio (HTTP 404)."
        429 -> "Se superó el límite de peticiones (HTTP 429). Agrega un app token o espera un momento."
        400 -> "Consulta no válida (HTTP 400): ${serverMessage(body)}"
        else -> "Error del servidor (HTTP $code)."
    }

    private fun serverMessage(body: String): String = try {
        JSONObject(body).optString("message").ifBlank { body.take(200) }
    } catch (e: Exception) {
        body.take(200)
    }

    /** Lee las columnas del dataset (metadatos); si falla, las deduce de una fila de ejemplo. */
    suspend fun columns(cfg: ApiConfig, datasetId: String): List<DataColumn> = withContext(Dispatchers.IO) {
        val id = checkId(datasetId)
        try {
            val text = execute(builder(cfg).url("$HOST/api/views/$id.json").get().build())
            val arr = JSONObject(text).optJSONArray("columns")
                ?: throw ApiException("El dataset no devolvió su lista de columnas.")
            val list = ArrayList<Pair<Int, DataColumn>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val field = o.optString("fieldName")
                if (field.isEmpty() || field.startsWith(":")) continue
                if (hasFlag(o.optJSONArray("flags"), "hidden")) continue
                list += o.optInt("position", i) to DataColumn(
                    fieldName = field,
                    label = o.optString("name", field).ifBlank { field },
                    type = o.optString("dataTypeName", "text"),
                )
            }
            list.sortedBy { it.first }.map { it.second }.ifEmpty { throw ApiException("El dataset no tiene columnas visibles.") }
        } catch (e: ApiException) {
            if (e.code == 404) throw e
            sampleColumns(cfg, id)
        }
    }

    private fun hasFlag(flags: JSONArray?, name: String): Boolean {
        if (flags == null) return false
        for (i in 0 until flags.length()) if (flags.optString(i) == name) return true
        return false
    }

    private fun sampleColumns(cfg: ApiConfig, id: String): List<DataColumn> {
        val rows = fetchBlocking(cfg, id, "SELECT *", 0, 1)
        val first = rows.firstOrNull() ?: throw ApiException("No se pudieron leer las columnas del dataset.")
        return first.keys().asSequence().map { key ->
            val numeric = first.optString(key).toDoubleOrNull() != null
            DataColumn(key, key, if (numeric) "number" else "text")
        }.toList()
    }

    /** Pide [limit] filas a partir de [offset]. El desplazamiento debe ser múltiplo de [limit]. */
    suspend fun fetch(cfg: ApiConfig, datasetId: String, soql: String, offset: Int, limit: Int): List<JSONObject> =
        withContext(Dispatchers.IO) { fetchBlocking(cfg, checkId(datasetId), soql, offset, limit) }

    private fun fetchBlocking(cfg: ApiConfig, id: String, soql: String, offset: Int, limit: Int): List<JSONObject> {
        val request = if (cfg.useV3) {
            if (cfg.token.isBlank()) {
                throw ApiException("La API v3 necesita un app token. Pégalo en Ajustes o cambia el modo a «v2 clásico».")
            }
            val body = JSONObject()
                .put("query", soql)
                .put("page", JSONObject().put("pageNumber", offset / limit + 1).put("pageSize", limit))
                .put("includeSynthetic", false)
            builder(cfg)
                .url("$HOST/api/v3/views/$id/query.json")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()
        } else {
            val url = "$HOST/resource/$id.json".toHttpUrl().newBuilder()
                .addQueryParameter("\$query", "$soql LIMIT $limit OFFSET $offset")
                .build()
            builder(cfg).url(url).get().build()
        }
        return parseRows(execute(request))
    }

    /** Acepta un arreglo JSON de filas o un objeto que contenga ese arreglo. */
    private fun parseRows(text: String): List<JSONObject> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        val arr: JSONArray = try {
            if (t.startsWith("[")) JSONArray(t) else findArray(JSONObject(t))
        } catch (e: org.json.JSONException) {
            throw ApiException("La respuesta de datos.gov.co no es un JSON válido.")
        }
        val rows = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        if (arr.length() > 0 && rows.isEmpty()) {
            throw ApiException("La respuesta no es una lista de objetos JSON, formato que la app no reconoce.")
        }
        return rows
    }

    private fun findArray(o: JSONObject): JSONArray {
        for (key in listOf("data", "results", "rows", "items")) o.optJSONArray(key)?.let { return it }
        val keys = o.keys()
        while (keys.hasNext()) o.optJSONArray(keys.next())?.let { return it }
        throw ApiException("La respuesta no contiene filas: ${o.optString("message").ifBlank { o.toString().take(160) }}")
    }
}
