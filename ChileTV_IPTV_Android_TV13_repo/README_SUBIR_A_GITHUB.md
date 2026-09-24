# TV Hispana MX10 v5.6 - Nacionales + Alplox

Sube el contenido de esta carpeta a la raíz de tu repositorio GitHub, conservando:

- `.github/workflows/build-apk.yml`
- `ChileTV_IPTV_Android_TV13_repo/ChileTV_IPTV_Android/`

## Cambios v5.6

- Nuevo apartado principal **Nacionales**.
- Fuente principal del apartado: **Alplox/json-teles (Chile)**.
- Fuentes alternativas: M3U.CL, IPTV-org y Free-TV.
- El filtro Nacionales prioriza TVN/TVN 3, 24 Horas, Mega/Meganoticias, Chilevisión/CHV Noticias, Canal 13/T13, La Red, TV+, UCV TV, CNN Chile y Telecanal cuando estén presentes en la fuente activa.
- Para Chilevisión se usa la ruta estable publicada en Alplox/Free-TV (`redirector.rudo.video`). La URL directa con `dpssid`, `sid` y `ndvc` no se fija porque esos parámetros parecen de sesión y pueden caducar.
- Se mantienen la búsqueda corregida, favoritos simplificados y el manejo de memoria de v5.5.

## Resultado de GitHub Actions

Artifact esperado: `TV-Hispana-MX10-v5-6-Nacionales-Alplox`

Versión: `1.6.2-mx10-nacionales-alplox` (11)
