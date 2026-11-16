package io.github.jfloresriera.reto10

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jfloresriera.reto10.data.Agg
import io.github.jfloresriera.reto10.data.ApiConfig
import io.github.jfloresriera.reto10.data.DataColumn
import io.github.jfloresriera.reto10.data.Filter
import io.github.jfloresriera.reto10.data.MapConfig
import io.github.jfloresriera.reto10.data.MunicipalityLayer
import io.github.jfloresriera.reto10.data.Prefs
import io.github.jfloresriera.reto10.data.Selected
import io.github.jfloresriera.reto10.data.Settings
import io.github.jfloresriera.reto10.data.SodaClient
import io.github.jfloresriera.reto10.data.Soql
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class UiState(
    // Esquema del dataset
    val schemaLoading: Boolean = false,
    val columns: List<DataColumn> = emptyList(),
    val schemaError: String? = null,
    // Consulta
    val filters: List<Filter> = emptyList(),
    val shownColumns: Set<String> = emptySet(),
    val orderBy: String? = null,
    val desc: Boolean = false,
    val municipality: Selected? = null,
    // Resultados
    val rows: List<Map<String, String>> = emptyList(),
    val rowsLoading: Boolean = false,
    val rowsError: String? = null,
    val hasMore: Boolean = false,
    val queried: Boolean = false,
    // Mapa
    val mapConfig: MapConfig = MapConfig(),
    val mapValues: Map<String, Double> = emptyMap(),
    val mapLoading: Boolean = false,
    val mapError: String? = null,
    val mapMissing: Int = 0,
    val selected: Selected? = null,
    val layerAvailable: Boolean = true,
    val layerCodes: Set<String> = emptySet(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val client = SodaClient()

    private val _settings = MutableStateFlow(prefs.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var schemaJob: Job? = null
    private var rowsJob: Job? = null
    private var mapJob: Job? = null

    init {
        _ui.update { it.copy(layerAvailable = MunicipalityLayer.exists(app)) }
        viewModelScope.launch {
            val codes = MunicipalityLayer.codes(app)
            _ui.update { it.copy(layerCodes = codes) }
        }
        loadSchema()
    }

    // ---------- Ajustes ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.update(transform)
        prefs.save(_settings.value)
    }

    fun setDataset(id: String) {
        updateSettings { it.copy(datasetId = id.trim().lowercase()) }
        loadSchema()
    }

    private fun config(): ApiConfig {
        val s = _settings.value
        return ApiConfig(s.token.ifBlank { BuildConfig.DATOS_APP_TOKEN }, s.apiMode)
    }

    private fun message(e: Exception): String = e.message ?: "Error desconocido."

    // ---------- Esquema ----------

    fun loadSchema() {
        schemaJob?.cancel()
        rowsJob?.cancel()
        mapJob?.cancel()
        schemaJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    schemaLoading = true, schemaError = null, columns = emptyList(), filters = emptyList(),
                    shownColumns = emptySet(), orderBy = null, rows = emptyList(), queried = false, hasMore = false,
                    rowsError = null, mapValues = emptyMap(), mapError = null, selected = null, municipality = null,
                )
            }
            try {
                val cols = client.columns(config(), _settings.value.datasetId)
                _ui.update { it.copy(schemaLoading = false, columns = cols, mapConfig = detectMapConfig(cols)) }
                loadMap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(schemaLoading = false, schemaError = message(e)) }
            }
        }
    }

    /** Intenta adivinar qué columna trae el código DANE y cuál conviene pintar. */
    private fun detectMapConfig(cols: List<DataColumn>): MapConfig {
        fun text(c: DataColumn) = (c.fieldName + " " + c.label).lowercase()
        val code = cols.firstOrNull { "dane" in text(it) && "mun" in text(it) }
            ?: cols.firstOrNull { "divipola" in text(it) }
            ?: cols.firstOrNull { "cod" in text(it) && "mun" in text(it) }
        val numeric = cols.filter { it.isNumeric && it.fieldName != code?.fieldName }
        val value = numeric.firstOrNull { "produc" in text(it) }
            ?: numeric.firstOrNull { "sembrada" in text(it) }
            ?: numeric.firstOrNull()
        val agg = if (value != null && "rendimiento" in text(value)) Agg.AVG else Agg.SUM
        return MapConfig(code?.fieldName, value?.fieldName, agg)
    }

    // ---------- Edición de la consulta ----------

    fun addFilter(f: Filter) = _ui.update { it.copy(filters = it.filters + f) }
    fun removeFilter(index: Int) = _ui.update { it.copy(filters = it.filters.filterIndexed { i, _ -> i != index }) }
    fun clearFilters() = _ui.update { it.copy(filters = emptyList(), municipality = null) }
    fun setShownColumns(cols: Set<String>) = _ui.update { it.copy(shownColumns = cols) }
    fun setOrder(col: String?, desc: Boolean) = _ui.update { it.copy(orderBy = col, desc = desc) }
    fun setMapConfig(cfg: MapConfig) {
        _ui.update { it.copy(mapConfig = cfg) }
        loadMap()
    }

    /** Valores distintos de una columna, para sugerirlos al crear un filtro. */
    suspend fun distinct(col: DataColumn): List<String> = try {
        client.fetch(config(), _settings.value.datasetId, Soql.distinct(col.fieldName), 0, 300)
            .map { it.optString(col.fieldName) }
            .filter { it.isNotBlank() && it != "null" }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    // ---------- Resultados ----------

    fun runQuery() {
        rowsJob?.cancel()
        rowsJob = viewModelScope.launch { loadRows(reset = true) }
        loadMap()
    }

    fun loadMore() {
        if (_ui.value.rowsLoading || !_ui.value.hasMore) return
        rowsJob = viewModelScope.launch { loadRows(reset = false) }
    }

    private suspend fun loadRows(reset: Boolean) {
        val st = _ui.value
        val offset = if (reset) 0 else st.rows.size
        _ui.update { it.copy(rowsLoading = true, rowsError = null, rows = if (reset) emptyList() else it.rows) }
        try {
            val shown = st.columns.filter { it.fieldName in st.shownColumns }.map { it.fieldName }
            val extra = st.municipality?.let { m ->
                st.columns.firstOrNull { it.fieldName == st.mapConfig.codeCol }?.let { Soql.codeMatch(it, m.code) }
            }
            val soql = Soql.rows(shown, st.filters, extra, st.orderBy, st.desc)
            val chunk = client.fetch(config(), _settings.value.datasetId, soql, offset, PAGE)
            val mapped = chunk.map { toRow(it, st.columns) }
            _ui.update {
                it.copy(
                    rows = if (reset) mapped else it.rows + mapped,
                    hasMore = chunk.size == PAGE, rowsLoading = false, queried = true,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(rowsLoading = false, rowsError = message(e), queried = true) }
        }
    }

    private fun toRow(o: JSONObject, columns: List<DataColumn>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun put(key: String) {
            val v = o.opt(key)
            out[key] = if (v == null || v == JSONObject.NULL) "" else v.toString()
        }
        columns.forEach { if (o.has(it.fieldName)) put(it.fieldName) }
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k !in out) put(k)
        }
        return out
    }

    // ---------- Mapa ----------

    fun loadMap() {
        val st = _ui.value
        val mc = st.mapConfig
        mapJob?.cancel()
        if (st.columns.isEmpty()) return
        if (mc.codeCol == null || (mc.valueCol == null && mc.agg != Agg.COUNT)) {
            _ui.update {
                it.copy(
                    mapValues = emptyMap(), mapLoading = false,
                    mapError = "No pude identificar las columnas del mapa. Toca «Ajustar» y elige el código DANE del municipio y la variable a colorear.",
                )
            }
            return
        }
        mapJob = viewModelScope.launch {
            _ui.update { it.copy(mapLoading = true, mapError = null) }
            try {
                val soql = Soql.mapAggregate(mc.codeCol, mc.valueCol, mc.agg, st.filters)
                val rows = fetchAll(soql)
                val values = HashMap<String, Double>()
                for (r in rows) {
                    val code = normalizeCode(r.optString("cod")) ?: continue
                    val v = r.optString("valor").toDoubleOrNull() ?: continue
                    val previous = values[code]
                    values[code] = when {
                        previous == null -> v
                        mc.agg == Agg.SUM || mc.agg == Agg.COUNT -> previous + v
                        mc.agg == Agg.MIN -> minOf(previous, v)
                        else -> maxOf(previous, v)
                    }
                }
                val layer = _ui.value.layerCodes
                val missing = if (layer.isEmpty()) 0 else values.keys.count { it !in layer }
                _ui.update { it.copy(mapValues = values, mapLoading = false, mapMissing = missing) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(mapLoading = false, mapError = message(e)) }
            }
        }
    }

    /** Descarga todas las páginas de una consulta agregada (la API limita las filas por página). */
    private suspend fun fetchAll(soql: String): List<JSONObject> {
        val all = ArrayList<JSONObject>()
        for (page in 0 until MAX_MAP_PAGES) {
            val chunk = client.fetch(config(), _settings.value.datasetId, soql, page * MAP_PAGE, MAP_PAGE)
            all += chunk
            if (chunk.size < MAP_PAGE) break
        }
        return all
    }

    /** '5001', '05001' o '5001.0' pasan a '05001'. Descarta códigos que no sean de municipio. */
    private fun normalizeCode(raw: String): String? {
        val digits = raw.trim().substringBefore('.').filter { it.isDigit() }
        return when (digits.length) {
            5 -> digits
            4 -> "0$digits"
            else -> null
        }
    }

    // ---------- Selección en el mapa ----------

    fun selectMunicipality(code: String, name: String, depto: String) =
        _ui.update { it.copy(selected = Selected(code, name, depto)) }

    fun clearSelection() = _ui.update { it.copy(selected = null) }

    /** Filtra los resultados por el municipio elegido en el mapa. */
    fun viewRecords() {
        _ui.update { it.copy(municipality = it.selected) }
        runQuery()
    }

    fun clearMunicipality() {
        _ui.update { it.copy(municipality = null) }
    }

    private companion object {
        const val PAGE = 50
        const val MAP_PAGE = 1000
        const val MAX_MAP_PAGES = 5
    }
}
