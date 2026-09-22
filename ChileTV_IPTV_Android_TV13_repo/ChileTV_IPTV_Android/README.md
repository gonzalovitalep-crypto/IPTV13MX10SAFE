# Chile TV IPTV · MX10 SAFE v1.3.0

Edición de compatibilidad para TV Stick/box genéricos MX10 con firmware Android modificado.

## Diferencias principales
- Inicio seguro sin AndroidX ni Media3.
- Interfaz basada solo en APIs nativas de Android.
- Reproductor `VideoView` / `MediaPlayer` nativo.
- Sin línea de tiempo en canales live, evitando el efecto de 10-30 segundos que vuelve a cero.
- Nombre del canal se oculta automáticamente y reaparece con control remoto/mouse/touch.
- Diagnóstico visible desde la primera pantalla.
- Botón para abrir un stream en un reproductor externo si el MediaPlayer del firmware falla.
- Lista Chile de IPTV-org y accesos a webs oficiales.

## Compatibilidad de compilación
- minSdk 21
- targetSdk 28 (modo de compatibilidad)
- compileSdk 35
- Java source/target 8
- Sin dependencias AndroidX

La app sigue utilizando la playlist pública de Chile de IPTV-org.

## Instalación en paralelo
Esta edición usa el applicationId `cl.chiletv.app.safe`, por lo que puede instalarse junto a la versión anterior para comparar sin desinstalarla.
