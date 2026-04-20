# Spec: Watermark And Text Overlay Review

## Problema y contexto

DuplicateFinder ya tiene flujos de revision individual para calidad y resolucion:

- `app/src/main/java/com/duplicatefinder/presentation/screens/quality/QualityReviewScreen.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/quality/QualityReviewViewModel.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/resolution/ResolutionReviewScreen.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/resolution/ResolutionReviewViewModel.kt`
- `app/src/main/java/com/duplicatefinder/domain/usecase/ScanQualityImagesUseCase.kt`
- `app/src/main/java/com/duplicatefinder/presentation/components/ReviewSharedComponents.kt`

La nueva funcionalidad debe reutilizar ese patron:

1. Escanear todas las imagenes de las carpetas seleccionadas.
2. Construir una cola 1 a 1 solo con candidatas relevantes.
3. Permitir decidir por imagen si se conserva, se elimina o se intenta limpiar.
4. Si se intenta limpiar, mostrar preview antes de tomar la decision final.

El problema a resolver es distinto al de duplicados o calidad:

- El usuario quiere detectar overlays invasivos en imagenes.
- El set objetivo incluye marcas de agua, firmas, handles con `@`, fechas, captions, texto de memes, stickers con texto y otros overlays visibles.
- La prioridad principal es minimizar falsos negativos.
- El dispositivo objetivo es de gama alta (`Samsung S23 Ultra`), por lo que se prioriza calidad del resultado por encima de latencia, peso del modelo y simplicidad.
- Los modelos deben poder descargarse una vez y luego funcionar completamente offline.

## Objetivos

- Reutilizar el flujo actual de scan + review individual, sin crear una UX completamente distinta.
- Ejecutar un pre-scan sobre todas las imagenes seleccionadas y rankear candidatas de mayor a menor invasividad.
- Agregar una tercera accion por item: `Eliminar marca de agua`.
- Al limpiar una imagen, mostrar preview antes de confirmar.
- Si el usuario conserva la version limpia, reemplazar la original en la misma carpeta y con el mismo nombre.
- Si el usuario elimina desde la preview, dejar cero copias finales visibles para el usuario.
- Si el usuario hace `skip`, conservar solo la original.
- Permitir uso totalmente offline despues de descargar el bundle de modelos.

## No objetivos

- No se intentara editar video.
- No se soportaran GIFs animados ni imagenes multi-frame en `v1`.
- No se garantizara preservacion total de EXIF/metadata al reemplazar la imagen.
- No se implementara edicion manual de mascara en `v1`.
- No se agregara procesamiento cloud ni subida de imagenes.
- No se intentara detectar semantica compleja no relacionada con overlays de texto o marcas.

## Enfoque elegido

Se adopta la opcion `C: cascada hibrida de recall alto`.

### Como funciona

El pipeline se divide en dos etapas de deteccion antes de ofrecer la revision:

1. **Etapa 1: detector de alto recall y bajo costo**
   - Corre sobre todas las imagenes seleccionadas.
   - Busca cualquier señal de overlay invasivo.
   - Prefiere incluir de mas antes que dejar pasar overlays reales.
   - Produce un score preliminar y una mascara/regiones candidatas.

2. **Etapa 2: refinamiento sobre candidatas**
   - Solo corre sobre imagenes marcadas por la primera etapa.
   - Refina score, mascara y clasificacion visual.
   - Reduce ruido antes de entrar a la cola de revision.

3. **Paso de limpieza**
   - Solo se ejecuta cuando el usuario pulsa `Eliminar marca de agua`.
   - Usa un modelo de inpainting offline de mayor costo para generar una vista previa limpia.

### Justificacion

- Minimiza falsos negativos mejor que un OCR puro.
- Evita correr el modelo mas caro sobre toda la galeria.
- Encaja con el patron actual de scan progresivo por lotes y cola 1 a 1.
- Se adapta bien a un dispositivo potente sin degradar de forma innecesaria el pre-scan.

## Terminologia del dominio

Para la implementacion interna, esta feature usara el termino **overlay** como termino tecnico general.

- **Overlay**: cualquier contenido superpuesto invasivo que el usuario podria querer remover.
- **Watermark action**: la accion visible para el usuario `Eliminar marca de agua`, aunque internamente cubra texto, firmas y otros overlays.
- **Candidate**: imagen que el pre-scan considera apta para revision.
- **Preview limpia**: resultado temporal del inpainting antes de confirmar reemplazo o descarte.

## Diseno detallado

### 1. Estructura de datos y modelos

#### 1.1 Nuevos modelos de dominio

`OverlayDetection`

- `image: ImageItem`
- `preliminaryScore: Float`
- `refinedScore: Float`
- `overlayCoverageRatio: Float`
- `maskBounds: List<OverlayRegion>`
- `maskConfidence: Float`
- `overlayKinds: Set<OverlayKind>`
- `stage: DetectionStage`
- `modelVersion: String`

Responsabilidad:
- Representar el resultado persistible del pre-scan.

`OverlayRegion`

- `left: Float`
- `top: Float`
- `right: Float`
- `bottom: Float`
- `confidence: Float`
- `kind: OverlayKind`

Responsabilidad:
- Representar una region normalizada de overlay sobre la imagen.

`OverlayKind`

- `TEXT`
- `HANDLE`
- `SIGNATURE`
- `DATE_STAMP`
- `LOGO`
- `CAPTION`
- `STICKER_TEXT`
- `UNKNOWN`

Responsabilidad:
- Etiquetar el tipo dominante de overlay detectado.

`OverlayReviewItem`

- `image: ImageItem`
- `detection: OverlayDetection`
- `rankScore: Float`

Responsabilidad:
- Item listo para entrar a la cola de revision individual.

`CleaningPreview`

- `sourceImage: ImageItem`
- `previewUri: Uri`
- `maskUri: Uri?`
- `modelVersion: String`
- `generationTimeMs: Long`
- `status: PreviewStatus`

Responsabilidad:
- Representar un preview temporal listo para mostrarse en UI.

`PreviewStatus`

- `GENERATING`
- `READY`
- `FAILED`
- `DISCARDED`

`DetectionStage`

- `STAGE_1_CANDIDATE`
- `STAGE_2_REFINED`

`OverlayScanState`

- `progress: ScanProgress`
- `items: List<OverlayReviewItem>`
- `candidateCount: Int`
- `scannedCount: Int`

Responsabilidad:
- Estado emitido por el scan incremental, alineado con `QualityScanState`.

#### 1.2 Nuevos modelos de UI

`OverlayReviewUiState`

- `isScanning`
- `isPaused`
- `isGeneratingPreview`
- `requiresFolderSelection`
- `error`
- `scanProgress`
- `items`
- `currentIndex`
- `keptImageIds`
- `markedForTrashIds`
- `cleaningRequestedIds`
- `completedCleanReplaceIds`
- `skippedPreviewIds`
- `previewState: CleaningPreview?`
- `minOverlayScore`
- `maxOverlayScore`

Responsabilidad:
- Mantener el mismo patron mental de `QualityReviewUiState`, pero agregando estado para preview y reemplazo.

`OverlayPreviewDecision`

- `KEEP_CLEANED_REPLACE_ORIGINAL`
- `DELETE_ALL`
- `SKIP_KEEP_ORIGINAL`

Responsabilidad:
- Modelar la decision final despues del preview.

### 2. Persistencia y cache

#### 2.1 Nueva tabla Room

Agregar una tabla `overlay_detections` con estas columnas:

- `imageId` `PRIMARY KEY`
- `path`
- `preliminaryScore`
- `refinedScore`
- `overlayCoverageRatio`
- `maskConfidence`
- `overlayKinds`
- `regionsJson`
- `dateModified`
- `size`
- `modelVersion`
- `createdAt`

Uso:
- Evitar reanalizar imagenes sin cambios cuando el modelo y el archivo siguen siendo validos.

Invalidacion:
- Si cambia `dateModified`
- Si cambia `size`
- Si cambia `modelVersion`

#### 2.2 Cache temporal de preview

Las imagenes limpiadas antes de confirmar no se guardan como resultado final.

Se almacenan temporalmente en cache interna de app:

- directorio sugerido: `cacheDir/overlay_preview/`
- cada preview se identifica por `imageId + modelVersion + timestamp`

Reglas:
- El preview se elimina al confirmar cualquier decision final.
- El preview se elimina si el usuario sale de la pantalla o si el proceso falla.
- El preview no entra a la papelera.

### 3. Componentes y responsabilidades

#### 3.1 Capa de presentacion

`OverlayReviewScreen`

Responsabilidades:
- UI principal del flujo.
- Reutilizar el layout base de review 1 a 1.
- Mostrar score, tipo de overlay y progreso.
- Exponer acciones:
  - `Conservar`
  - `Eliminar`
  - `Eliminar marca de agua`
- Abrir preview modal o pantalla dedicada cuando haya resultado limpio.

`OverlayReviewViewModel`

Responsabilidades:
- Orquestar el scan incremental.
- Ordenar la cola por score descendente.
- Gestionar transiciones de estado del preview.
- Ejecutar acciones finales:
  - conservar original
  - mover original a papelera
  - reemplazar original con limpia
  - descartar preview

`HomeScreen`

Responsabilidad nueva:
- Exponer un nuevo entry point para esta revision desde Home, como hoy hace con calidad y resolucion.

`NavGraph`

Responsabilidad nueva:
- Agregar una nueva ruta dedicada para `overlay_review`.

#### 3.2 Capa de dominio

`ScanOverlayCandidatesUseCase`

Responsabilidades:
- Leer carpetas seleccionadas.
- Consultar cantidad total.
- Procesar por lotes.
- Ejecutar la etapa 1 sobre todas las imagenes.
- Ejecutar la etapa 2 solo sobre candidatas.
- Emitir `OverlayScanState` incremental.
- Ordenar items por score refinado descendente.

`GenerateOverlayPreviewUseCase`

Responsabilidades:
- Validar que el bundle de modelos este disponible.
- Preparar bitmap de entrada.
- Ejecutar inferencia de limpieza.
- Persistir preview temporal.
- Retornar `CleaningPreview`.

`ApplyOverlayPreviewDecisionUseCase`

Responsabilidades:
- Aplicar la decision final del usuario sobre el preview.
- Reemplazar la imagen original si corresponde.
- Mover original a papelera cuando corresponda.
- Limpiar cache temporal del preview.

`EnsureOverlayModelBundleUseCase`

Responsabilidades:
- Determinar si el bundle requerido ya esta descargado.
- Exponer estado de descarga y disponibilidad offline.

#### 3.3 Capa de datos

`OverlayRepository`

Responsabilidades:
- Leer/escribir cache de deteccion.
- Ejecutar inferencia de etapa 1 y etapa 2.
- Construir `OverlayDetection`.

`OverlayModelBundleRepository`

Responsabilidades:
- Descargar una vez el bundle de modelos.
- Validar integridad del bundle.
- Exponer ruta local y version activa.
- Permitir uso offline despues de la descarga.

`OverlayCleaningRepository`

Responsabilidades:
- Ejecutar el modelo de inpainting.
- Persistir preview temporal.
- Aplicar reemplazo final de archivo.

### 4. Runtime ML y estrategia de modelos

#### 4.1 Regla de producto

La app no incluye los pesos del modelo dentro del APK en `v1`.

La primera vez que el usuario quiera usar `Eliminar marca de agua`, la app debe:

1. Detectar si el bundle local existe.
2. Ofrecer una descarga explicita.
3. Guardar el bundle en almacenamiento interno de app.
4. Funcionar completamente offline despues de esa descarga.

#### 4.2 Bundle versionado

El bundle local debe estar versionado y descrito por un manifest simple:

- `bundleVersion`
- `detectorStage1Path`
- `detectorStage2Path`
- `inpainterPath`
- `inputSizeStage1`
- `inputSizeStage2`
- `inputSizeInpainter`

Decisiones:
- La app solo usa un bundle activo a la vez.
- No se autoactualiza durante una sesion de revision.
- Si el bundle falta o esta corrupto, la accion `Eliminar marca de agua` queda bloqueada y la UI ofrece descargar/reintentar.

#### 4.3 Estrategia tecnica

`v1` debe diseñarse para soportar:

- detector barato de alto recall en etapa 1
- detector/segmentador de refinamiento en etapa 2
- modelo de inpainting para limpieza

El contrato de la app se define alrededor del bundle y no alrededor de un modelo unico hardcodeado.

Esto permite:

- cambiar pesos sin reescribir el flujo UI
- mantener separada la logica de producto del proveedor exacto del modelo
- hacer tuning posterior sin romper la arquitectura

### 5. Flujo de datos entre componentes

#### 5.1 Pre-scan y ranking

1. El usuario entra a `OverlayReviewScreen` desde Home.
2. `OverlayReviewViewModel.startReview()` valida carpetas seleccionadas.
3. `ScanOverlayCandidatesUseCase` consulta `ImageRepository.getImageCount(folders)`.
4. Las imagenes se procesan por lotes, igual que en `ScanQualityImagesUseCase`.
5. Para cada imagen:
   - intentar leer cache valida de deteccion
   - si no existe, ejecutar etapa 1
   - si etapa 1 supera umbral de inclusion, ejecutar etapa 2
6. Solo entran a la cola final las imagenes con `refinedScore >= reviewThreshold`.
7. La cola se ordena por `refinedScore DESC`, luego por `overlayCoverageRatio DESC`.
8. El ViewModel expone el primer item no decidido.

#### 5.2 Acciones por item

`Conservar`

- Marca la imagen como revisada y conserva la original intacta.
- Avanza al siguiente item.

`Eliminar`

- Marca la imagen para mover a papelera.
- Sigue el patron actual del app.
- No elimina de inmediato si el flujo sigue usando lote final.

`Eliminar marca de agua`

- Verifica bundle local.
- Si no existe, abre flujo de descarga.
- Si existe, genera preview limpio.
- No modifica todavia la imagen original.

#### 5.3 Flujo de preview

1. El usuario pulsa `Eliminar marca de agua`.
2. La UI pasa a estado `isGeneratingPreview = true`.
3. `GenerateOverlayPreviewUseCase`:
   - carga la imagen
   - prepara input
   - genera mascara final
   - ejecuta inpainting
   - guarda preview temporal
4. La UI muestra comparacion original vs preview limpio.
5. El usuario decide:
   - `Conservar limpia`
   - `Eliminar ambas`
   - `Skip`

#### 5.4 Aplicacion de decision final

`Conservar limpia`

- Se escribe una nueva version del archivo en la misma carpeta y con el mismo nombre.
- El reemplazo debe sentirse atomico desde la perspectiva del usuario:
  1. escribir archivo limpio temporal
  2. preparar una copia de seguridad temporal controlada por la app
  3. reemplazar el contenido visible en galeria con la version limpia
  4. validar que la imagen final exista y tenga tamaño mayor a cero
  5. eliminar residuos temporales
- La implementacion exacta puede usar `MediaStore` mas una copia de seguridad temporal, pero el resultado observable debe ser siempre el mismo:
  - una sola imagen final visible
  - mismo nombre
  - misma carpeta
- Se acepta perdida parcial de metadata.
- La imagen queda como una sola copia final visible: la limpia.

`Eliminar ambas`

- Como la imagen limpia aun es solo un preview temporal, no existe una segunda copia final en galeria.
- La accion hace:
  1. descartar preview temporal
  2. mover la original a papelera
- Resultado final visible para el usuario: ninguna copia en galeria.
- Nota: en `v1`, "eliminar" mantiene la semantica actual del app y por tanto la original entra a la papelera, no a borrado permanente inmediato.

`Skip`

- Se descarta preview temporal.
- La original permanece intacta.
- El item se considera revisado.

### 6. Reemplazo de archivo y politica de papelera

#### 6.1 Regla general

La app debe seguir usando su sistema actual de papelera para la original cuando la decision final implique eliminarla.

#### 6.2 Reemplazo de original

El reemplazo no debe escribir encima del `Uri` existente sin control.

Debe hacerse como operacion gestionada por repositorio con rollback seguro:

- escribir salida limpia a archivo temporal
- crear una ruta de recuperacion temporal para la original
- ejecutar el reemplazo visible en galeria
- validar que el item final exista y tenga tamaño mayor a cero
- limpiar backup y preview temporal solo despues de validar exito

Si cualquier paso falla:

- la original se conserva o se restaura
- el item parcial visible se elimina
- la UI muestra error

### 7. UX y comportamiento esperado

#### 7.1 Entrada al flujo

Se agrega un nuevo CTA en Home junto a `Review Image Quality` y `Review Low Resolution`.

Nombre recomendado de UI:

- `Review Watermarks`

#### 7.2 Orden de revision

La cola debe mostrarse de mas probable a menos probable.

La puntuacion debe ser interpretable por UI:

- score alto = overlay mas probable o mas invasivo

#### 7.3 Estados vacios

La pantalla debe distinguir entre:

- no hay carpetas seleccionadas
- no hay imagenes
- no hay candidatas detectadas
- el filtro actual oculta todas las candidatas

#### 7.4 Filtro de score

El flujo debe incluir un filtro por score similar a como `QualityReview` usa rango por calidad.

Uso:

- permitir revisar solo overlays mas invasivos primero
- permitir ampliar rango si el usuario quiere revisar candidatos marginales

### 8. Casos edge y manejo

#### 8.1 Imagen muy grande

Problema:
- riesgo de memoria alta durante deteccion o inpainting

Manejo:
- two-pass decode
- downsample controlado por input size del modelo
- limite por pixeles antes de cargar full bitmap

#### 8.2 Imagen cambio durante la revision

Problema:
- cache invalida o archivo reemplazado externamente

Manejo:
- invalidar por `dateModified` y `size`
- antes de aplicar decision final, volver a resolver `ImageItem` actual
- si ya no existe, mostrar error y descartar preview

#### 8.3 Preview fallido

Problema:
- el modelo no pudo limpiar, se quedo sin memoria o el bundle esta corrupto

Manejo:
- no tocar la original
- limpiar preview parcial
- permitir `Retry` o volver a acciones normales

#### 8.4 Overlay demasiado grande

Problema:
- memes o captions ocupan gran parte de la imagen y el inpainting puede degradar mucho el resultado

Manejo:
- permitir igualmente preview
- mostrar area estimada afectada
- no autoaplicar nunca el reemplazo

#### 8.5 Falsos positivos

Problema:
- texto diegetico o contenido de la escena puede entrar como candidato

Manejo:
- se acepta en `v1` porque el producto prioriza recall
- el usuario puede `Conservar`
- la etapa 2 y el ranking deben empujar arriba lo mas invasivo

#### 8.6 GIF animado, HEIC especial o formato no soportado

Manejo:
- excluir de limpieza en `v1`
- si aparece en scan, marcar como no elegible para preview y exponer mensaje claro

#### 8.7 Proceso interrumpido por salir de la pantalla

Manejo:
- cancelar jobs activos
- limpiar preview temporal
- no dejar archivos limpios huérfanos en cache

### 9. Estrategia de testing

#### 9.1 Unit tests

Cubrir:

- ordenamiento por score descendente
- logica de filtro por score
- transiciones de `OverlayReviewUiState`
- decision final:
  - conservar limpia
  - eliminar ambas
  - skip
- invalidacion de cache por `dateModified`, `size` y `modelVersion`

#### 9.2 Tests de use case

Cubrir:

- `ScanOverlayCandidatesUseCase` emite progreso incremental y solo retorna candidatas elegibles
- `GenerateOverlayPreviewUseCase` falla de forma segura si no hay bundle
- `ApplyOverlayPreviewDecisionUseCase` no deja archivos duplicados tras reemplazo exitoso
- en fallo de reemplazo, la original queda intacta

#### 9.3 Tests de repositorio

Cubrir:

- serializacion/deserializacion de `regionsJson`
- escritura y lectura de `overlay_detections`
- limpieza de cache temporal de preview

#### 9.4 Tests de UI/ViewModel

Cubrir:

- arranque sin carpetas seleccionadas
- cola vacia sin candidatas
- generacion de preview
- bloqueo correcto mientras se genera preview
- avance al siguiente item tras cada decision

#### 9.5 Verificacion manual

Dataset minimo recomendado para aceptar `v1`:

- 20 imagenes con firmas pequenas
- 20 imagenes con `@handle`
- 20 imagenes con captions o memes
- 20 imagenes sin overlays
- 10 imagenes con overlays muy grandes

### 10. Decisiones explicitas de alcance

#### Incluido en `v1`

- nueva pantalla de review individual para overlays
- pre-scan con score y ranking
- accion `Eliminar marca de agua`
- descarga opcional del bundle de modelos
- uso offline posterior a la descarga
- preview antes de confirmar
- reemplazo de original con mismo nombre y misma carpeta
- uso de papelera para eliminar la original

#### Excluido de `v1`

- edicion manual de mascara
- soporte para video
- soporte para GIF animado
- batch clean sin preview por item
- conservacion garantizada de EXIF completo
- seleccion de multiples bundles/modelos desde UI
- sincronizacion cloud

### 11. Criterios de verificacion medibles

La feature se considera lista cuando se cumplan todos estos criterios:

1. Con carpetas seleccionadas, el pre-scan construye una cola solo con candidatas y la muestra ordenada por `rankScore` descendente.
2. En un dataset manual de 70 imagenes con overlays reales, al menos `95%` aparecen como candidatas en la cola final.
3. En un dataset manual de 20 imagenes sin overlays, se aceptan falsos positivos, pero la app no debe autoeliminar ni autoaplicar limpieza en ninguna.
4. La accion `Eliminar marca de agua` nunca modifica la original antes de mostrar preview.
5. La decision `Conservar limpia` deja exactamente una imagen final visible en galeria, con el mismo nombre y en la misma carpeta que la original.
6. La decision `Eliminar ambas` deja cero imagenes finales visibles y la original queda en papelera.
7. La decision `Skip` deja la original intacta y elimina el preview temporal.
8. Si falla el reemplazo final, la original sigue disponible y no queda un duplicado parcial visible.
9. Despues de descargar el bundle una vez, el flujo de preview funciona sin red.
10. En un `S23 Ultra`, generar preview de una imagen de hasta `12 MP` debe tardar menos de `8 s` en `p90`.
11. El pre-scan de `500` imagenes debe poder completarse sin crash por memoria en el dispositivo objetivo.

### 12. Riesgos y mitigaciones

#### Riesgo: falsos positivos altos

Mitigacion:
- score visible
- filtro por score
- accion segura de `Conservar`
- no hay autoaplicacion

#### Riesgo: bundle pesado

Mitigacion:
- descarga opcional solo cuando se necesita
- bundle fuera del APK
- reuse local posterior

#### Riesgo: limpieza visualmente mala en overlays grandes

Mitigacion:
- preview obligatorio
- nunca reemplazo automatico

#### Riesgo: reemplazo de archivo fragil en MediaStore

Mitigacion:
- flujo transaccional con verificacion intermedia
- rollback conservando la original

### 13. Compatibilidad con la arquitectura actual

La implementacion debe imitar los patrones ya presentes:

- scan incremental por lotes como `ScanQualityImagesUseCase`
- cola 1 a 1 controlada por ViewModel
- estados vacios y summary reutilizando `ReviewSharedComponents`
- ruta dedicada en `NavGraph`
- entry point desde `HomeScreen`
- persistencia y cache en Room

La diferencia principal es que este flujo tiene una sub-maquina de estados adicional para preview y reemplazo.

### 14. Resultado esperado del usuario

Para el usuario final, la experiencia debe sentirse asi:

1. Entra a `Review Watermarks`.
2. La app revisa sus carpetas y ordena primero las imagenes mas sospechosas.
3. Ve una imagen a la vez.
4. Decide `Conservar`, `Eliminar` o `Eliminar marca de agua`.
5. Si limpia, ve preview antes de confirmar.
6. Si acepta la limpia, la imagen queda reemplazada.
7. Si no le gusta, puede eliminarla o saltarla sin perder accidentalmente la original.

## Estado de aprobacion

Spec lista para revision del usuario. No se debe implementar hasta recibir aprobacion explicita de esta spec.

Cuando la spec este aprobada: `Spec aprobada. Usa $plan para convertirla en un plan de implementacion paso a paso.`
