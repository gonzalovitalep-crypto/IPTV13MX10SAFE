# TV Hispana MX10 Memory Safe v5.4

Aplicación Android para TV Box / Stick Rockchip MX10 con runtime real API 25 y memoria limitada.

## Cambios v5.4

- Catálogo paginado: solo 96 fichas por página para reducir presión de memoria y GC.
- Caché de playlists en disco con TTL de 30 minutos; no mantiene M3U completas en RAM.
- Descarga y parser M3U por streaming, sin `String.split()` sobre listas de varios MB.
- Cancela cargas anteriores al cambiar de fuente.
- Al abrir un canal libera el catálogo de memoria; al volver lo reconstruye desde caché.
- El player corre en proceso separado (`:player`) para aislar memoria y fallos del firmware.
- Antes de usar `MediaPlayer`, realiza un probe HTTP de la señal.
- En HLS master selecciona una variante de hasta 720p y ~2.5 Mbps cuando existe.
- Identifica el error `1 / -2147483648` como error de sistema de bajo nivel del MediaPlayer.
- Detecta VLC (`org.videolan.vlc`). Si está instalado, lo abre directamente; si no, ofrece la página oficial de instalación.
- El debug ahora informa heap Java, cache y conserva el último evento del player entre procesos.

## Fuentes

Se mantienen las regiones y fuentes de v5.3: Chile, Latinoamérica, España/Europa, EE.UU. Hispano y Todo en español.

## APK

Compilar con GitHub Actions usando `.github/workflows/build-apk.yml` del paquete GitHub-ready.
