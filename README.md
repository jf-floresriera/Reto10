# Reto 10 · EVA Explorer

Aplicación Android (Kotlin, Jetpack Compose) que consume los servicios web de **datos.gov.co** (Catálogo de Datos Abiertos de Colombia) y muestra las **Evaluaciones Agropecuarias Municipales (EVA)** en un mapa interactivo por municipio.

- **Mapa coroplético:** cada municipio se pinta según la variable elegida (producción, área sembrada, rendimiento, etc.). Al tocar un municipio se ven su valor y sus registros.
- **Consulta de todas las columnas:** el constructor de filtros se arma con las columnas reales del dataset (nombre y tipo), con valores sugeridos, selección de columnas y orden.
- **Datasets:** `2pnw-mmge` (EVA 2007–2018) y `uejq-wxrr` (EVA 2019–2023, por verificar). También acepta cualquier otro identificador de datos.gov.co.
- **JSON con el SDK de Android:** todas las respuestas se procesan con `org.json` (`JSONObject` y `JSONArray`), como pide el reto.

## Cómo funciona la consulta

| Modo | Petición | Requiere token |
|------|----------|----------------|
| v3 | `POST https://www.datos.gov.co/api/v3/views/{id}/query.json` con cuerpo `{"query": "...", "page": {...}}` | Sí (`X-App-Token`) |
| v2 | `GET https://www.datos.gov.co/resource/{id}.json?$query=...` | No (con límite de peticiones) |

El modo **Automático** usa v3 si hay token y v2 si no. Las columnas se leen de `https://www.datos.gov.co/api/views/{id}.json`.

El mapa pide al servidor un valor agregado por municipio (`SELECT cod, SUM(valor) ... GROUP BY cod`), así que no se descarga la tabla completa.

## App token

1. Crea una cuenta en datos.gov.co, abre tu perfil y entra a la configuración de desarrollador.
2. Crea un **App Token**.
3. Pégalo en la pestaña **Ajustes** de la app, o compílalo dentro del APK:
   - Local: agrega `datos.appToken=TU_TOKEN` en `local.properties` (el archivo está en `.gitignore`).
   - GitHub Actions: crea el secreto `DATOS_APP_TOKEN` en el repositorio.

## Capa de municipios

El shapefile oficial (septiembre 2026, MAGNA-SIRGAS 2018) está en `data/raw/municipios_shp/` y se guarda con Git LFS. No va dentro del APK: se convierte una vez a un GeoJSON liviano.

```bash
pip install -r tools/requirements.txt
python tools/shp_a_geojson.py
```

El script reproyecta a WGS84, simplifica los polígonos (0.002°, unos 220 m), conserva `cod` (código DANE de 5 dígitos), `nombre` y `depto`, y escribe `app/src/main/assets/municipios.geojson`. Si pesa más de 8 MB, usa `--tol 0.004`. Después, súbelo con un commit normal:

```bash
git add app/src/main/assets/municipios.geojson
git commit -m "Agregar capa GeoJSON simplificada de municipios"
```

El cruce entre los datos y el mapa se hace por el **código DANE del municipio**. Los municipios cuyo código no aparezca en la capa se cuentan y se avisan en la pantalla del mapa.

## Compilar

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. En GitHub, el workflow **Compilar APK** lo deja como artefacto en la pestaña Actions.

## Estructura

```
app/src/main/java/io/github/jfloresriera/reto10/
  data/      Models, Soql (consultas), SodaClient (API + org.json), Prefs, MunicipalityLayer
  domain/    Choropleth (clasificación en cuantiles y colores)
  ui/        theme, components, screens (Mapa, Consultar, Resultados, Ajustes)
  AppViewModel.kt, MainActivity.kt
tools/       shp_a_geojson.py
data/raw/    shapefile original (Git LFS)
```

## Limitaciones conocidas

- Las columnas del mapa (código DANE y variable) se detectan por su nombre. Si no se reconocen, se eligen a mano con el botón **Ajustar**.
- Los municipios creados o con código cambiado entre versiones del Marco Geoestadístico pueden no coincidir con las filas de datos más antiguas.
- La v3 de SODA limita las filas por página; el mapa pide varias páginas hasta completar la agregación.
