# TV Hispana MX10 v5.5

Sube el contenido de esta carpeta contenedora a la raíz de tu repositorio manteniendo esta estructura:

```text
.github/workflows/build-apk.yml
ChileTV_IPTV_Android_TV13_repo/ChileTV_IPTV_Android/
```

## Cambios v5.5

- El buscador mantiene el teclado abierto después de escribir la primera letra.
- El filtrado usa un pequeño debounce para disminuir trabajo en el MX10.
- Pulsa Buscar/Enter para cerrar el teclado y volver a la grilla.
- Favoritos más fáciles: pulsa directamente la estrella visible de cada canal.
- Con control remoto también puedes marcar/quitar favorito con MENU, botón amarillo, BOOKMARK o `*` si están disponibles.
- Se mantiene mantener-OK como alternativa.
- Fuente chilena M3U.CL incorporada de forma explícita: `https://m3u.cl/lista/CL.m3u`.
- Versión de la app: `1.6.1-mx10-search-favorites` (`versionCode 10`).

## Compilación

Al hacer Commit en `main`, GitHub Actions ejecutará `Compilar TV Hispana MX10 v5.5 Search Favorites`.
Descarga el artefacto `TV-Hispana-MX10-v5-5-Search-Favorites` y dentro encontrarás `app-debug.apk`.
