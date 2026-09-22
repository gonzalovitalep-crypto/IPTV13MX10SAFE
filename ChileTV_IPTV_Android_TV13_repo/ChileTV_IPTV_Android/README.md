# Chile TV IPTV · MX10 Legacy v5

Edición específica para TV Box MX10 que reporta Android 13 pero ejecuta API 25 (Android 7.1 a nivel de SDK).

## Cambios principales

- `targetSdk 25`, `minSdk 21`, `compileSdk 35`.
- ExoPlayer Legacy 2.18.7 para HLS/DASH, compatible con API 16+.
- Fallback automático al `VideoView/MediaPlayer` nativo si ExoPlayer no abre una señal.
- Límite de video a 720p / 4 Mbps para reducir presión sobre Rockchip RK322x y 128 MB de heap.
- Buffer reducido para equipos con poca memoria.
- Sin barra de tiempo para señales en vivo.
- La franja con el nombre del canal se oculta tras unos segundos y vuelve con mouse/control remoto.
- Botón "Abrir externo" cuando un canal requiere otro reproductor.
- Varias fuentes chilenas seleccionables desde la app:
  - Verificados: `https://dearbulut.github.io/iptv/playlists/country/cl.m3u`
  - IPTV-org: `https://iptv-org.github.io/iptv/countries/cl.m3u`
  - M3U.CL: `https://m3u.cl/lista/CL.m3u`
  - Free-TV: `https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8` (filtrada a Chile)
- Filtro "Nacionales" activado por defecto.
- Accesos a señales web oficiales de Chilevisión, TVN/24 Horas, Mega, Canal 13/T13, TV+, etc.

## Uso con control remoto

- Flechas: navegar.
- OK/Enter sobre un canal: reproducir.
- OK durante reproducción: pausa/reanuda.
- Atrás: volver.
- Mantener pulsado sobre un canal: favorito.
- Botón `Fuente`: cambia entre las cuatro listas.

## Nota

Las listas enlazan señales públicas de terceros. La disponibilidad de cada canal puede cambiar, requerir geolocalización, cabeceras HTTP o un reproductor distinto.
