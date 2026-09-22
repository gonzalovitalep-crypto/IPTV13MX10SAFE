# Chile TV IPTV · MX10 ULTRA SAFE v5.1

Edición para MX10/RK322x que muestra "Android 13" pero ejecuta apps como API 25.

## Diseño de compatibilidad

- Sin AndroidX.
- Sin Media3.
- Sin ExoPlayer ni otras bibliotecas de reproducción al iniciar.
- `minSdk 21`, `targetSdk 25`, `compileSdk 35`.
- El inicio no crea `VideoView` ni inicializa `MediaPlayer`.
- Lista IPTV con varias fuentes chilenas.
- Al elegir un canal se muestra un selector seguro:
  1. **Reproductor externo** (recomendado; VLC/MX Player).
  2. Reproductor nativo del MX10 bajo demanda.
  3. Abrir URL/navegador.
- Diagnóstico accesible desde el launcher, antes de cargar listas o video.

## Fuentes configuradas

- IPTV-org Chile.
- M3U.CL.
- Free-TV, filtrando Chile.
- Fuente verificada configurada en `ChannelRepository`.

La disponibilidad de cada stream depende de la fuente y puede cambiar.
