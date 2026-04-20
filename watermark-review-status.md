# Watermark Review Status

## Estado actual

La rama `feature/watermark-review` ya incluye la base arquitectural de la nueva funcionalidad:

- modelos de dominio para overlays, preview y decisiones
- cache Room para detecciones de overlay
- contratos y repositorios para deteccion, bundle de modelos y limpieza
- casos de uso para scan, disponibilidad de bundle, preview y decision final
- `OverlayReviewViewModel`
- `OverlayReviewScreen`
- integracion en `HomeScreen` y `NavGraph`
- strings y dependencias iniciales de `TensorFlow Lite`
- tests unitarios iniciales para estado, use cases y ViewModel

## Lo que SI esta implementado

- flujo de review 1 a 1
- ranking por score de overlay
- accion `Remove Watermark`
- preview temporal en cache interna
- decision final:
  - conservar original
  - eliminar a papelera
  - conservar limpia reemplazando original
  - skip
- wiring para bundle descargable
- estructura lista para correr inferencia offline

## Lo que TODAVIA falta para tener inpainting real

### 1. Modelo real de inpainting

Hoy `OverlayCleaningRepositoryImpl` no ejecuta inpainting real.

Estado actual:

- genera un preview temporal copiando la imagen original a cache
- permite validar el flujo de UI y decisiones
- no elimina realmente la marca de agua

Pendiente:

- definir un modelo `TFLite` real compatible con Android
- incluirlo dentro del bundle descargable
- documentar el contrato exacto del modelo:
  - input image shape
  - input mask shape
  - output tensor shape
  - normalizacion de pixeles
  - formato de canales

### 2. Generacion real de mascara

Hoy `OverlayRepositoryImpl` usa heuristicas simples para producir:

- score preliminar
- score refinado
- regiones candidatas

Eso sirve como scaffold, pero no como detector final de producto.

Pendiente:

- reemplazar heuristicas por una cascada real:
  - etapa 1 de alto recall
  - etapa 2 de refinamiento
- usar salida real del modelo para:
  - regiones
  - confidence
  - coverage ratio
  - kinds

### 3. Pipeline tensorial real

Pendiente dentro de `OverlayCleaningRepositoryImpl`:

- cargar modelo desde el bundle
- crear `Interpreter`
- preparar bitmap al tamaño del modelo
- rasterizar o construir mascara binaria o soft mask
- correr inferencia
- reconstruir bitmap de salida
- persistir preview limpio real

### 4. Bundle remoto real

La arquitectura ya soporta manifest y descarga, pero falta el insumo real:

- URL valida del manifest
- archivos reales del bundle
- versionado real de assets
- validacion de integridad mas fuerte

## Bloqueo de verificacion local

En esta sesion no fue posible ejecutar `./gradlew` porque no hay `Java Runtime` disponible.

Por eso quedaron pendientes estas verificaciones:

- compilacion Kotlin/Android
- ejecucion de tests unitarios
- ensamblado `debug`
- pruebas manuales en dispositivo

## Siguiente paso recomendado

Para completar la feature de forma real:

1. conseguir o definir el modelo `TFLite` de deteccion y el de inpainting
2. adaptar `OverlayRepositoryImpl` al output real del detector
3. adaptar `OverlayCleaningRepositoryImpl` al input/output real del inpainter
4. compilar y ejecutar tests con un entorno que tenga Java
5. validar en dispositivo fisico el reemplazo de archivo y el preview

## Nota de alcance

La rama actual deja la funcionalidad en estado de:

- arquitectura implementada
- flujo UX implementado
- placeholders seguros para ML real
- lista para ser completada cuando existan modelo y entorno de compilacion
