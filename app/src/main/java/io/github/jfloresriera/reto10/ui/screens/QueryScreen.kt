@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.jfloresriera.reto10.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jfloresriera.reto10.AppViewModel
import io.github.jfloresriera.reto10.data.DataColumn
import io.github.jfloresriera.reto10.data.Filter
import io.github.jfloresriera.reto10.data.Op
import io.github.jfloresriera.reto10.data.PRESETS
import io.github.jfloresriera.reto10.data.Soql
import io.github.jfloresriera.reto10.ui.components.AppCard
import io.github.jfloresriera.reto10.ui.components.DropdownField
import io.github.jfloresriera.reto10.ui.components.InfoNote
import io.github.jfloresriera.reto10.ui.components.PrimaryButton
import io.github.jfloresriera.reto10.ui.components.ScreenBar
import io.github.jfloresriera.reto10.ui.components.SecondaryButton
import io.github.jfloresriera.reto10.ui.components.SectionTitle
import io.github.jfloresriera.reto10.ui.theme.ErrorRed
import io.github.jfloresriera.reto10.ui.theme.TextSoft
import kotlinx.coroutines.launch

@Composable
fun QueryScreen(vm: AppViewModel, onRun: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showAddFilter by remember { mutableStateOf(false) }
    var showColumns by remember { mutableStateOf(false) }
    var customId by remember(settings.datasetId) { mutableStateOf(settings.datasetId) }

    Column(Modifier.fillMaxSize()) {
        ScreenBar("Consultar")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Conjunto de datos
            AppCard {
                SectionTitle("1 · Conjunto de datos")
                DropdownField(
                    label = "Conjuntos sugeridos",
                    valueText = PRESETS.firstOrNull { it.id == settings.datasetId }?.title ?: "Otro (${settings.datasetId})",
                    options = PRESETS,
                    optionText = { "${it.title}  (${it.id})" },
                    onSelect = { vm.setDataset(it.id) },
                )
                OutlinedTextField(
                    value = customId,
                    onValueChange = { customId = it },
                    label = { Text("O escribe otro identificador (xxxx-xxxx)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                TextButton(
                    onClick = { vm.setDataset(customId) },
                    enabled = customId.isNotBlank() && customId.trim().lowercase() != settings.datasetId,
                ) { Text("Cargar este dataset") }
                if (ui.schemaLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                ui.schemaError?.let {
                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.loadSchema() }) { Text("Reintentar") }
                }
                if (ui.columns.isNotEmpty()) {
                    Text(
                        "${ui.columns.size} columnas disponibles",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }
            }

            // 2. Filtros
            AppCard {
                SectionTitle("2 · Filtros")
                if (ui.filters.isEmpty() && ui.municipality == null) {
                    Text("Sin filtros: se consulta todo el dataset.", style = MaterialTheme.typography.bodySmall, color = TextSoft)
                }
                ui.filters.forEachIndexed { index, f ->
                    FilterChip(
                        selected = true,
                        onClick = { vm.removeFilter(index) },
                        label = { Text("${f.column.label} ${f.op.label} ${f.value}", maxLines = 2) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Quitar filtro") },
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                ui.municipality?.let { m ->
                    FilterChip(
                        selected = true,
                        onClick = { vm.clearMunicipality() },
                        label = { Text("Municipio: ${m.name} (${m.code})") },
                        trailingIcon = { Icon(Icons.Filled.Close, "Quitar municipio") },
                    )
                }
                TextButton(onClick = { showAddFilter = true }, enabled = ui.columns.isNotEmpty()) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar filtro")
                }
            }

            // 3. Columnas y orden
            AppCard {
                SectionTitle("3 · Columnas y orden")
                val shownText = if (ui.shownColumns.isEmpty()) "Todas (${ui.columns.size})" else "${ui.shownColumns.size} seleccionadas"
                Text("Columnas a mostrar", style = MaterialTheme.typography.bodySmall, color = TextSoft)
                OutlinedButton(
                    onClick = { showColumns = true },
                    enabled = ui.columns.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(shownText, modifier = Modifier.weight(1f)) }
                DropdownField(
                    label = "Ordenar por",
                    valueText = ui.columns.firstOrNull { it.fieldName == ui.orderBy }?.label ?: "Sin orden",
                    options = listOf<DataColumn?>(null) + ui.columns,
                    optionText = { it?.label ?: "Sin orden" },
                    onSelect = { vm.setOrder(it?.fieldName, ui.desc) },
                    enabled = ui.columns.isNotEmpty(),
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = ui.desc, onCheckedChange = { vm.setOrder(ui.orderBy, it) })
                    Spacer(Modifier.width(10.dp))
                    Text("Orden descendente", style = MaterialTheme.typography.bodyMedium)
                }
            }

            InfoNote(
                "Los filtros se aplican a la lista de resultados y al mapa. " +
                    "La lista trae 50 filas por página; el mapa agrega todo el dataset por municipio.",
            )

            PrimaryButton(
                text = "Consultar",
                icon = Icons.Outlined.Search,
                enabled = ui.columns.isNotEmpty() && !ui.rowsLoading,
                onClick = {
                    vm.runQuery()
                    onRun()
                },
            )
            SecondaryButton(
                text = "Limpiar filtros",
                onClick = { vm.clearFilters() },
            )
        }
    }

    if (showAddFilter) {
        AddFilterDialog(
            columns = ui.columns,
            vm = vm,
            onDismiss = { showAddFilter = false },
            onAdd = {
                vm.addFilter(it)
                showAddFilter = false
            },
        )
    }
    if (showColumns) {
        ColumnsDialog(
            columns = ui.columns,
            initial = ui.shownColumns,
            onDismiss = { showColumns = false },
            onApply = {
                vm.setShownColumns(it)
                showColumns = false
            },
        )
    }
}

@Composable
private fun AddFilterDialog(
    columns: List<DataColumn>,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onAdd: (Filter) -> Unit,
) {
    var column by remember { mutableStateOf(columns.firstOrNull()) }
    var op by remember { mutableStateOf(columns.firstOrNull()?.let { Op.forColumn(it).first() }) }
    var value by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingSuggestions by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar filtro") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DropdownField(
                    label = "Columna",
                    valueText = column?.label ?: "Elegir…",
                    options = columns,
                    optionText = { it.label },
                    onSelect = {
                        column = it
                        op = Op.forColumn(it).first()
                        value = ""
                        suggestions = emptyList()
                        error = null
                    },
                )
                column?.let { c ->
                    DropdownField(
                        label = "Condición",
                        valueText = op?.label ?: "Elegir…",
                        options = Op.forColumn(c),
                        optionText = { it.label },
                        onSelect = { op = it },
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it; error = null },
                        label = { Text(if (c.isNumeric) "Valor (número)" else "Valor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                loadingSuggestions = true
                                suggestions = vm.distinct(c)
                                loadingSuggestions = false
                            }
                        },
                        enabled = !loadingSuggestions,
                    ) { Text(if (loadingSuggestions) "Buscando valores…" else "Ver valores existentes") }
                    if (suggestions.isNotEmpty()) {
                        DropdownField(
                            label = "Valores existentes (${suggestions.size})",
                            valueText = "Elegir uno…",
                            options = suggestions,
                            optionText = { it },
                            onSelect = { value = it; error = null },
                        )
                    }
                }
                error?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = column
                val o = op
                if (c == null || o == null) return@TextButton
                val filter = Filter(c, o, value)
                try {
                    Soql.condition(filter) // valida el valor antes de guardar el filtro
                    onAdd(filter)
                } catch (e: IllegalArgumentException) {
                    error = e.message
                }
            }) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ColumnsDialog(
    columns: List<DataColumn>,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Columnas a mostrar") },
        text = {
            Column {
                Row {
                    TextButton(onClick = { chosen = columns.map { it.fieldName }.toSet() }) { Text("Marcar todas") }
                    TextButton(onClick = { chosen = emptySet() }) { Text("Quitar todas") }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(columns) { c ->
                        val checked = c.fieldName in chosen
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { chosen = if (checked) chosen - c.fieldName else chosen + c.fieldName }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Text(c.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    "Si no marcas ninguna, se muestran todas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Todas marcadas equivale a no restringir columnas.
                onApply(if (chosen.size == columns.size) emptySet() else chosen)
            }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
