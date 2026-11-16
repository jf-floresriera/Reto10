package io.github.jfloresriera.reto10.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/** Acceso a la capa de municipios que va dentro del APK (assets/municipios.geojson). */
object MunicipalityLayer {
    const val ASSET = "municipios.geojson"

    fun exists(context: Context): Boolean =
        runCatching { context.assets.open(ASSET).close() }.isSuccess

    /** Códigos DANE presentes en la capa. Lee solo las propiedades, sin cargar las geometrías. */
    suspend fun codes(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val out = HashSet<String>()
        runCatching {
            JsonReader(InputStreamReader(context.assets.open(ASSET), Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    if (r.nextName() == "features") {
                        r.beginArray()
                        while (r.hasNext()) readFeature(r, out)
                        r.endArray()
                    } else {
                        r.skipValue()
                    }
                }
                r.endObject()
            }
        }
        out
    }

    private fun readFeature(r: JsonReader, out: MutableSet<String>) {
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "properties" && r.peek() == JsonToken.BEGIN_OBJECT) {
                r.beginObject()
                while (r.hasNext()) {
                    if (r.nextName() == "cod" && r.peek() == JsonToken.STRING) out += r.nextString() else r.skipValue()
                }
                r.endObject()
            } else {
                r.skipValue()
            }
        }
        r.endObject()
    }
}
