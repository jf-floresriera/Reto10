#!/usr/bin/env python3
"""Convierte la capa shapefile de municipios en un GeoJSON liviano para la app.

Qué hace:
  1. Lee data/raw/municipios_shp/Munpio.shp (MAGNA-SIRGAS 2018 Origen Nacional, en metros).
  2. Reproyecta a WGS84 (EPSG:4326), que es lo que usa el mapa.
  3. Simplifica los polígonos (por defecto 0.002 grados, unos 220 m).
  4. Conserva solo tres atributos: cod (código DANE de 5 dígitos), nombre y depto.
  5. Redondea las coordenadas a 5 decimales y escribe el GeoJSON compacto.

Uso (desde la raíz del repositorio):
  pip install -r tools/requirements.txt
  python tools/shp_a_geojson.py

Si el script no encuentra las columnas por su nombre, imprime las que existen;
indica cuáles usar con --cod, --nombre y --depto.
"""
import argparse
import json
import sys
from pathlib import Path

import geopandas as gpd
from shapely.geometry import mapping

CANDIDATAS = {
    "cod": ["mpcodigo", "mpio_cdpmp", "cod_dane", "codigo_dane", "cod_mpio", "mpio_ccdgo", "codigo"],
    "nombre": ["mpnombre", "mpio_cnmbr", "nombre", "municipio", "nom_mpio"],
    "depto": ["depto", "dpto_cnmbr", "departamento", "nom_depto", "dpto"],
}


def buscar_columna(gdf, clave, forzada):
    """Devuelve el nombre real de la columna (sin distinguir mayúsculas) o None."""
    por_minuscula = {c.lower(): c for c in gdf.columns if c != "geometry"}
    if forzada:
        if forzada.lower() not in por_minuscula:
            sys.exit(f"La columna '{forzada}' no existe. Columnas: {list(por_minuscula.values())}")
        return por_minuscula[forzada.lower()]
    for candidata in CANDIDATAS[clave]:
        if candidata in por_minuscula:
            return por_minuscula[candidata]
    return None


def normalizar_codigo(valor):
    """'5001', '05001' o '5001.0' -> '05001'. Devuelve '' si no se puede."""
    texto = str(valor).strip().split(".")[0]
    digitos = "".join(ch for ch in texto if ch.isdigit())
    if len(digitos) == 4:
        digitos = "0" + digitos
    return digitos if len(digitos) == 5 else ""


def redondear(coords, decimales=5):
    """Redondea recursivamente las coordenadas y descarta la altura (Z) si existe."""
    if coords and isinstance(coords[0], (int, float)):
        return [round(coords[0], decimales), round(coords[1], decimales)]
    return [redondear(c, decimales) for c in coords]


def main():
    raiz = Path(__file__).resolve().parent.parent
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--entrada", default=str(raiz / "data/raw/municipios_shp/Munpio.shp"))
    p.add_argument("--salida", default=str(raiz / "app/src/main/assets/municipios.geojson"))
    p.add_argument("--tol", type=float, default=0.002, help="tolerancia de simplificación en grados")
    p.add_argument("--cod", help="columna con el código DANE del municipio")
    p.add_argument("--nombre", help="columna con el nombre del municipio")
    p.add_argument("--depto", help="columna con el nombre del departamento")
    a = p.parse_args()

    gdf = gpd.read_file(a.entrada)
    print(f"Registros leídos : {len(gdf)}")
    print(f"Columnas         : {[c for c in gdf.columns if c != 'geometry']}")
    print(f"CRS de origen    : {gdf.crs.name if gdf.crs else 'SIN CRS'}")
    if gdf.crs is None:
        sys.exit("La capa no tiene sistema de coordenadas (.prj). No se puede reproyectar.")

    c_cod = buscar_columna(gdf, "cod", a.cod)
    c_nom = buscar_columna(gdf, "nombre", a.nombre)
    c_dep = buscar_columna(gdf, "depto", a.depto)
    if not c_cod:
        sys.exit("No encontré la columna del código DANE. Usa --cod NOMBRE_DE_LA_COLUMNA.")
    print(f"Usando           : cod={c_cod}  nombre={c_nom}  depto={c_dep}")

    gdf = gdf[gdf.geometry.notna() & ~gdf.geometry.is_empty].to_crs(4326)
    gdf["geometry"] = gdf.geometry.simplify(a.tol, preserve_topology=True)
    gdf = gdf[~gdf.geometry.is_empty]

    features, sin_codigo = [], 0
    for _, fila in gdf.iterrows():
        cod = normalizar_codigo(fila[c_cod])
        if not cod:
            sin_codigo += 1
            continue
        geom = mapping(fila.geometry)
        features.append({
            "type": "Feature",
            "properties": {
                "cod": cod,
                "nombre": str(fila[c_nom]).strip() if c_nom else cod,
                "depto": str(fila[c_dep]).strip() if c_dep else "",
            },
            "geometry": {"type": geom["type"], "coordinates": redondear(geom["coordinates"])},
        })

    salida = Path(a.salida)
    salida.parent.mkdir(parents=True, exist_ok=True)
    with open(salida, "w", encoding="utf-8") as f:
        json.dump({"type": "FeatureCollection", "features": features}, f, ensure_ascii=False, separators=(",", ":"))

    mb = salida.stat().st_size / 1_048_576
    print(f"Municipios       : {len(features)}  (sin código válido: {sin_codigo})")
    print(f"Archivo          : {salida}  ({mb:.1f} MB)")
    if mb > 8:
        print("Aviso: pesa más de 8 MB. Prueba --tol 0.004 para una capa más liviana.")


if __name__ == "__main__":
    main()
