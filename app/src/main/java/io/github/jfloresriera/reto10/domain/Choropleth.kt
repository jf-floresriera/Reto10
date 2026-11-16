package io.github.jfloresriera.reto10.domain

import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale

data class LegendItem(val label: String, val color: Int)

/**
 * Resultado de clasificar los valores por municipio.
 * [expression] es una expresión de estilo de MapLibre (JSON) que asigna un color a cada código DANE,
 * o null si no hay datos.
 */
class ChoroplethResult(val expression: String?, val legend: List<LegendItem>)

object Choropleth {
    /** Gris de los municipios sin dato. */
    const val NO_DATA: Int = 0xFFE0E0E0.toInt()

    private val EMPTY = ChoroplethResult(null, emptyList())

    /** Clasifica en cuantiles (hasta tantas clases como colores tenga [palette]). */
    fun build(values: Map<String, Double>, palette: List<Int>): ChoroplethResult {
        if (values.isEmpty() || palette.isEmpty()) return EMPTY

        val sorted = values.values.sorted()
        val k = palette.size
        val thresholds = (1 until k)
            .map { sorted[(it * (sorted.size - 1)) / k] }
            .distinct()
        val classes = thresholds.size + 1

        // Con menos clases que colores, se reparten los colores a lo largo de la rampa.
        val colorOf = { cls: Int ->
            if (classes == 1) palette[k - 1] else palette[Math.round(cls * (k - 1).toFloat() / (classes - 1))]
        }

        val codesByClass = HashMap<Int, MutableList<String>>()
        for ((code, v) in values) {
            val cls = thresholds.count { v >= it }
            codesByClass.getOrPut(cls) { mutableListOf() }.add(code)
        }

        val expression = JSONArray().put("match").put(JSONArray().put("get").put("cod"))
        for (cls in 0 until classes) {
            val codes = codesByClass[cls] ?: continue
            expression.put(JSONArray(codes)).put(hex(colorOf(cls)))
        }
        expression.put(hex(NO_DATA))

        val legend = (0 until classes).map { cls ->
            val label = when {
                classes == 1 -> format(sorted.first())
                cls == 0 -> "< ${format(thresholds[0])}"
                cls == classes - 1 -> "≥ ${format(thresholds[cls - 1])}"
                else -> "${format(thresholds[cls - 1])} – ${format(thresholds[cls])}"
            }
            LegendItem(label, colorOf(cls))
        }
        return ChoroplethResult(expression.toString(), legend)
    }

    private fun hex(color: Int): String = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

    fun format(v: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale("es", "CO"))
        nf.maximumFractionDigits = if (kotlin.math.abs(v) >= 100) 0 else 2
        return nf.format(v)
    }
}
