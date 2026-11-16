package io.github.jfloresriera.reto10.data

/**
 * Construye consultas SoQL. Los nombres de columna se validan (solo letras, números y guion bajo)
 * y los valores se escapan, porque se interpolan en el texto de la consulta.
 */
object Soql {
    private val IDENT = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val NUMBER = Regex("^-?\\d+(\\.\\d+)?$")

    fun ident(name: String): String {
        require(IDENT.matches(name)) { "Nombre de columna no válido: $name" }
        return name
    }

    fun quote(value: String): String = "'" + value.replace("'", "''") + "'"

    private fun symbol(op: Op): String = when (op) {
        Op.EQ -> "="
        Op.NE -> "!="
        Op.CONTAINS -> "LIKE"
        Op.GT -> ">"
        Op.GTE -> ">="
        Op.LT -> "<"
        Op.LTE -> "<="
    }

    /** Condición WHERE para un filtro. Lanza IllegalArgumentException con un mensaje legible. */
    fun condition(f: Filter): String {
        val col = ident(f.column.fieldName)
        val raw = f.value.trim()
        require(raw.isNotEmpty()) { "Falta el valor del filtro sobre «${f.column.label}»." }

        if (f.column.isNumeric) {
            require(f.op != Op.CONTAINS) { "«contiene» no aplica a la columna numérica «${f.column.label}»." }
            val n = raw.replace(',', '.')
            require(NUMBER.matches(n)) { "«$raw» no es un número válido para «${f.column.label}»." }
            return "$col ${symbol(f.op)} $n"
        }
        return when (f.op) {
            Op.CONTAINS -> "upper($col) LIKE ${quote("%" + raw.uppercase() + "%")}"
            Op.EQ, Op.NE ->
                if (f.column.isText) "upper($col) ${symbol(f.op)} ${quote(raw.uppercase())}"
                else "$col ${symbol(f.op)} ${quote(raw)}"
            else -> "$col ${symbol(f.op)} ${quote(raw)}"
        }
    }

    /** Coincidencia con un código DANE, sin importar si la columna guarda ceros a la izquierda. */
    fun codeMatch(col: DataColumn, code: String): String {
        val c = ident(col.fieldName)
        val short = code.trimStart('0').ifEmpty { "0" }
        return if (col.isNumeric) "$c = $short" else "($c = ${quote(code)} OR $c = ${quote(short)})"
    }

    private fun where(filters: List<Filter>, extra: String?): String =
        (filters.map { condition(it) } + listOfNotNull(extra)).joinToString(" AND ")

    fun rows(
        columns: List<String>,
        filters: List<Filter>,
        extra: String?,
        orderBy: String?,
        desc: Boolean,
    ): String {
        val select = if (columns.isEmpty()) "*" else columns.joinToString(", ") { ident(it) }
        val w = where(filters, extra)
        return buildString {
            append("SELECT ").append(select)
            if (w.isNotEmpty()) append(" WHERE ").append(w)
            if (orderBy != null) append(" ORDER BY ").append(ident(orderBy)).append(if (desc) " DESC" else " ASC")
        }
    }

    /** Un valor agregado por municipio. El resultado trae las claves «cod» y «valor». */
    fun mapAggregate(codeCol: String, valueCol: String?, agg: Agg, filters: List<Filter>): String {
        val code = ident(codeCol)
        val expr = if (agg == Agg.COUNT || valueCol == null) "COUNT(*)" else "${agg.fn}(${ident(valueCol)})"
        val w = where(filters, null)
        return buildString {
            append("SELECT ").append(code).append(" AS cod, ").append(expr).append(" AS valor")
            if (w.isNotEmpty()) append(" WHERE ").append(w)
            append(" GROUP BY ").append(code).append(" ORDER BY ").append(code)
        }
    }

    fun distinct(col: String): String {
        val c = ident(col)
        return "SELECT $c GROUP BY $c ORDER BY $c"
    }
}
