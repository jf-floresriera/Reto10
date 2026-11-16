package io.github.jfloresriera.reto10.data

/** Columna de un dataset de Socrata (datos.gov.co). */
data class DataColumn(val fieldName: String, val label: String, val type: String) {
    val isNumeric: Boolean get() = type in NUMERIC_TYPES
    val isText: Boolean get() = type == "text"

    private companion object {
        val NUMERIC_TYPES = setOf("number", "money", "percent", "double")
    }
}

enum class Op(val label: String) {
    EQ("igual a"),
    NE("distinto de"),
    CONTAINS("contiene"),
    GT("mayor que"),
    GTE("mayor o igual que"),
    LT("menor que"),
    LTE("menor o igual que");

    companion object {
        fun forColumn(c: DataColumn): List<Op> = when {
            c.isText -> listOf(EQ, CONTAINS, NE)
            else -> listOf(EQ, NE, GT, GTE, LT, LTE)
        }
    }
}

data class Filter(val column: DataColumn, val op: Op, val value: String)

/** Función de agregación SoQL para colorear el mapa por municipio. */
enum class Agg(val label: String, val fn: String) {
    SUM("Suma", "SUM"),
    AVG("Promedio", "AVG"),
    MAX("Máximo", "MAX"),
    MIN("Mínimo", "MIN"),
    COUNT("Conteo de registros", "COUNT"),
}

enum class ApiMode(val label: String) {
    AUTO("Automático"),
    V3("v3 (con token)"),
    V2("v2 clásico"),
}

data class DatasetInfo(val id: String, val title: String)

val PRESETS = listOf(
    DatasetInfo("2pnw-mmge", "EVA 2007–2018"),
    DatasetInfo("uejq-wxrr", "EVA 2019–2023"),
)

data class Settings(
    val token: String = "",
    val apiMode: ApiMode = ApiMode.AUTO,
    val theme: String = "green",
    val datasetId: String = PRESETS.first().id,
)

/** Municipio elegido en el mapa. */
data class Selected(val code: String, val name: String, val depto: String)

data class MapConfig(
    val codeCol: String? = null,
    val valueCol: String? = null,
    val agg: Agg = Agg.SUM,
)
