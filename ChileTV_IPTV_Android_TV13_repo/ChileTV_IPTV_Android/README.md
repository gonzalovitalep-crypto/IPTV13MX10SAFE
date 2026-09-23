# TV Hispana · MX10 Ultra Safe v5.3

Versión específica para MX10/RK322x cuyo firmware muestra Android 13 pero expone API 25.

## Cambio v5.3

- Nueva pantalla principal con fichas grandes por región:
  - Chile
  - Latinoamérica
  - España / Europa
  - EE.UU. Hispano
  - Todo en español
- Dentro de cada región, los canales se muestran como fichas compactas en una cuadrícula adaptable para aprovechar mejor la pantalla del TV.
- Las fichas son deliberadamente de texto (sin descargar logos) para reducir memoria en el MX10, que reporta 128 MB por app.
- Búsqueda por nombre, país o categoría.
- Fuentes alternativas por región mediante el botón **Fuente**.
- Favoritos con pulsación larga.
- Mantiene navegación por control remoto reforzada de v5.2.
- Mantiene selector seguro: reproductor externo, reproductor nativo bajo demanda o navegador.

## Fuentes públicas configuradas

### Chile
- dearbulut / Chile verificado
- IPTV-org Chile
- M3U.CL Chile
- Free-TV, filtrado a Chile

### Latinoamérica
- IPTV-org Hispanoamérica
- IPTV-org Latinoamérica, filtrado a países hispanohablantes
- IPTV-org idioma español, filtrado a Latinoamérica
- dearbulut idioma español, filtrado a Latinoamérica
- M3U.CL combinado con Argentina, Bolivia, Chile, Colombia, Ecuador y México

### España / Europa
- IPTV-org España
- IPTV-org idioma español, filtrado a España/Andorra
- M3U.CL España
- Free-TV filtrado a España/Andorra

### EE.UU. Hispano
- IPTV-org idioma español, filtrado a emisiones de EE.UU.
- dearbulut idioma español, filtrado a emisiones de EE.UU.

### Todo en español
- IPTV-org idioma español
- dearbulut idioma español

La disponibilidad de cada stream depende de la fuente y puede cambiar.

## Compatibilidad MX10

- Sin AndroidX.
- Sin Media3.
- Sin ExoPlayer.
- `minSdk 21`, `targetSdk 25`, `compileSdk 35`.
- Java 8.
- No inicializa `VideoView` al abrir la app.
