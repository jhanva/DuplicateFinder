# Plan: Implementacion de Watermark And Text Overlay Review

## Estado de entrada

- Spec cargada desde [spec.md](/Users/a6007098/projects/sandbox/DuplicateFinder/spec.md)
- Enfoque aprobado: `C: cascada hibrida de recall alto`
- Restriccion clave: no implementar en `main`

## Supuestos tecnicos para ejecutar este plan

1. `v1` usara `TensorFlow Lite` en CPU para deteccion y limpieza.
2. El bundle de modelos sera versionado y descargable despues del primer uso.
3. El codigo quedara listo para descargar desde una URL configurable; antes de probar en dispositivo real debe existir una URL valida para el manifest del bundle.
4. Mientras no exista la URL real, todas las tareas de dominio, UI, cache, estado y reemplazo siguen siendo ejecutables con fakes y tests unitarios.

## Preflight

Antes de ejecutar tareas:

```bash
git checkout -b feature/watermark-review
./gradlew :app:testDebugUnitTest
```

Resultado esperado:

- `git checkout` cambia a `feature/watermark-review`
- Gradle termina con `BUILD SUCCESSFUL`

## Mapa de archivos

### Crear

- `app/src/main/java/com/duplicatefinder/domain/model/OverlayDetection.kt`
- `app/src/main/java/com/duplicatefinder/domain/model/OverlayReviewItem.kt`
- `app/src/main/java/com/duplicatefinder/domain/model/CleaningPreview.kt`
- `app/src/main/java/com/duplicatefinder/domain/model/OverlayScanState.kt`
- `app/src/main/java/com/duplicatefinder/domain/repository/OverlayRepository.kt`
- `app/src/main/java/com/duplicatefinder/domain/repository/OverlayModelBundleRepository.kt`
- `app/src/main/java/com/duplicatefinder/domain/repository/OverlayCleaningRepository.kt`
- `app/src/main/java/com/duplicatefinder/domain/usecase/ScanOverlayCandidatesUseCase.kt`
- `app/src/main/java/com/duplicatefinder/domain/usecase/EnsureOverlayModelBundleUseCase.kt`
- `app/src/main/java/com/duplicatefinder/domain/usecase/GenerateOverlayPreviewUseCase.kt`
- `app/src/main/java/com/duplicatefinder/domain/usecase/ApplyOverlayPreviewDecisionUseCase.kt`
- `app/src/main/java/com/duplicatefinder/data/local/db/entities/OverlayDetectionEntity.kt`
- `app/src/main/java/com/duplicatefinder/data/local/db/dao/OverlayDetectionDao.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayRepositoryImpl.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayModelBundleRepositoryImpl.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayCleaningRepositoryImpl.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiState.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModel.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewScreen.kt`
- `app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiStateTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/ScanOverlayCandidatesUseCaseTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/EnsureOverlayModelBundleUseCaseTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/GenerateOverlayPreviewUseCaseTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/ApplyOverlayPreviewDecisionUseCaseTest.kt`
- `app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModelTest.kt`

### Modificar

- `app/src/main/java/com/duplicatefinder/data/local/db/AppDatabase.kt`
- `app/src/main/java/com/duplicatefinder/di/DatabaseModule.kt`
- `app/src/main/java/com/duplicatefinder/di/RepositoryModule.kt`
- `app/src/main/java/com/duplicatefinder/di/AppModule.kt`
- `app/src/main/java/com/duplicatefinder/presentation/navigation/NavGraph.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/home/HomeScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `app/src/test/java/com/duplicatefinder/domain/TestFixtures.kt`

## Fase 1: Modelos, estado y cache

### Tarea 1: Crear modelos de dominio para overlays

**Archivos:** crear `domain/model/OverlayDetection.kt`, `domain/model/CleaningPreview.kt`

**Paso 1 — Test que falla:**

Agregar en `app/src/test/java/com/duplicatefinder/domain/TestFixtures.kt` helpers de prueba:

```kotlin
fun testOverlayDetection(image: ImageItem, score: Float = 0.9f): OverlayDetection
fun testCleaningPreview(image: ImageItem): CleaningPreview
```

Luego crear un smoke test en `OverlayReviewUiStateTest.kt` que instancie esos modelos y falle por clases inexistentes.

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewUiStateTest"
```

Resultado esperado:

- `BUILD FAILED`
- error `Unresolved reference: OverlayDetection` o equivalente

**Paso 3 — Implementar:**

- En `OverlayDetection.kt` definir:
  - `OverlayKind`
  - `DetectionStage`
  - `OverlayRegion`
  - `OverlayDetection`
  - `OverlayReviewItem`
- En `CleaningPreview.kt` definir:
  - `PreviewStatus`
  - `OverlayPreviewDecision`
  - `CleaningPreview`
  - `OverlayScanState`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewUiStateTest"
```

Resultado esperado:

- compilacion del paquete `domain.model` sin errores

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/domain/model/OverlayDetection.kt app/src/main/java/com/duplicatefinder/domain/model/CleaningPreview.kt app/src/test/java/com/duplicatefinder/domain/TestFixtures.kt app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiStateTest.kt
git commit -m "feat(domain): add overlay review models"
```

### Tarea 2: Crear estado de UI para la revision de overlays

**Archivos:** crear `presentation/screens/overlay/OverlayReviewUiState.kt`, crear `OverlayReviewUiStateTest.kt`

**Paso 1 — Test que falla:**

Escribir estos tests con nombre exacto y aserciones concretas:

- `items are filtered by score range and sorted by rank score descending`
  - crear 3 `OverlayReviewItem` con `rankScore` `0.95f`, `0.80f`, `0.30f`
  - fijar rango `minOverlayScore = 0.50f`, `maxOverlayScore = 1.00f`
  - verificar que `filteredOverlayItems.map { it.image.id } == listOf(1L, 2L)`
- `review is complete when current item is null and scanning is finished`
  - crear estado con `isScanning = false`, `currentIndex = -1`, `overlayItems` no vacio, `keptImageIds` conteniendo todos los ids
  - verificar `isReviewComplete == true`
- `preview ready state exposes the generated preview`
  - crear estado con `previewState.status = PreviewStatus.READY`
  - verificar que `previewState?.previewUri` no es nulo

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewUiStateTest"
```

Resultado esperado:

- `BUILD FAILED`
- errores por `OverlayReviewUiState` inexistente

**Paso 3 — Implementar:**

- Copiar el patron de `QualityReviewUiState`
- Agregar:
  - `overlayItems`
  - `minOverlayScore`
  - `maxOverlayScore`
  - `isGeneratingPreview`
  - `previewState: CleaningPreview?`
  - `cleaningRequestedIds`
  - `completedCleanReplaceIds`
  - `skippedPreviewIds`
- Exponer computed properties:
  - `filteredOverlayItems`
  - `currentItem`
  - `isReviewComplete`
  - `hasNoResults`
  - `hasNoFilterMatches`
  - `showFullScanProgress`
  - `showInlineScanProgress`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewUiStateTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiState.kt app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiStateTest.kt
git commit -m "feat(ui): add overlay review ui state"
```

### Tarea 3: Agregar entidad y DAO para cache de detecciones

**Archivos:** crear `data/local/db/entities/OverlayDetectionEntity.kt`, crear `data/local/db/dao/OverlayDetectionDao.kt`

**Paso 1 — Test que falla:**

Extender `app/src/test/java/com/duplicatefinder/domain/TestFixtures.kt` con mapper smoke test inputs y crear `ScanOverlayCandidatesUseCaseTest.kt` con un test que dependa de `OverlayDetectionEntity`.

- Test exacto:
  - nombre: `cached detection is reused when size date and model version match`
  - asercion: el fake repository no debe ejecutar deteccion cuando el cache coincide en `size`, `dateModified` y `modelVersion`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`
- errores por DAO o entidad inexistentes

**Paso 3 — Implementar:**

- `OverlayDetectionEntity` con columnas:
  - `imageId`, `path`, `preliminaryScore`, `refinedScore`, `overlayCoverageRatio`
  - `maskConfidence`, `overlayKinds`, `regionsJson`, `dateModified`, `size`
  - `modelVersion`, `createdAt`
- `OverlayDetectionDao` con:
  - `getByImageIds(imageIds: List<Long>): List<OverlayDetectionEntity>`
  - `insertAll(entities: List<OverlayDetectionEntity>)`
  - `deleteByImageId(imageId: Long)`
  - `deleteAll()`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest"
```

Resultado esperado:

- compilacion del paquete `data.local.db` sin errores

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/data/local/db/entities/OverlayDetectionEntity.kt app/src/main/java/com/duplicatefinder/data/local/db/dao/OverlayDetectionDao.kt app/src/test/java/com/duplicatefinder/domain/usecase/ScanOverlayCandidatesUseCaseTest.kt app/src/test/java/com/duplicatefinder/domain/TestFixtures.kt
git commit -m "feat(db): add overlay detection cache schema"
```

### Tarea 4: Registrar tabla, migracion y provider de DAO

**Archivos:** modificar `AppDatabase.kt`, modificar `DatabaseModule.kt`

**Paso 1 — Test que falla:**

En `ScanOverlayCandidatesUseCaseTest.kt` agregar un test que instancie el repositorio fake con `OverlayDetectionDao` provider esperado.

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`
- referencia faltante a `overlayDetectionDao()` o migracion

**Paso 3 — Implementar:**

- Subir `AppDatabase` a version `3`
- Registrar `OverlayDetectionEntity`
- Agregar `abstract fun overlayDetectionDao(): OverlayDetectionDao`
- Crear `MIGRATION_2_3` con `CREATE TABLE overlay_detections`
- Registrar la migracion en `DatabaseModule`
- Proveer `OverlayDetectionDao`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/data/local/db/AppDatabase.kt app/src/main/java/com/duplicatefinder/di/DatabaseModule.kt
git commit -m "feat(db): wire overlay detection migration"
```

## Fase 2: Contratos y casos de uso de dominio

### Tarea 5: Crear contratos de repositorio y fakes de prueba

**Archivos:** crear `domain/repository/OverlayRepository.kt`, crear `domain/repository/OverlayModelBundleRepository.kt`, crear `domain/repository/OverlayCleaningRepository.kt`, modificar `TestFixtures.kt`

**Paso 1 — Test que falla:**

Crear tests de dominio con estas dependencias:

```kotlin
class ScanOverlayCandidatesUseCaseTest
class EnsureOverlayModelBundleUseCaseTest
class GenerateOverlayPreviewUseCaseTest
class ApplyOverlayPreviewDecisionUseCaseTest
```

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.*"
```

Resultado esperado:

- `BUILD FAILED`
- interfaces faltantes

**Paso 3 — Implementar:**

- `OverlayRepository`:
  - `getCachedDetections(imageIds, modelVersion)`
  - `detectOverlayCandidates(images, modelVersion)`
  - `saveDetections(detections)`
- `OverlayModelBundleRepository`:
  - `getActiveBundleInfo()`
  - `ensureBundleAvailable()`
  - `downloadBundle()`
- `OverlayCleaningRepository`:
  - `generatePreview(image, detection, bundleInfo)`
  - `applyDecision(image, preview, decision)`
  - `discardPreview(preview)`
- En `TestFixtures.kt` agregar `BaseOverlayRepositoryFake`, `BaseOverlayModelBundleRepositoryFake`, `BaseOverlayCleaningRepositoryFake`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.*"
```

Resultado esperado:

- compilacion de los tests de dominio

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/domain/repository/OverlayRepository.kt app/src/main/java/com/duplicatefinder/domain/repository/OverlayModelBundleRepository.kt app/src/main/java/com/duplicatefinder/domain/repository/OverlayCleaningRepository.kt app/src/test/java/com/duplicatefinder/domain/TestFixtures.kt
git commit -m "feat(domain): add overlay repository contracts"
```

### Tarea 6: Implementar `ScanOverlayCandidatesUseCase`

**Archivos:** crear `domain/usecase/ScanOverlayCandidatesUseCase.kt`, completar `ScanOverlayCandidatesUseCaseTest.kt`

**Paso 1 — Test que falla:**

Agregar tests exactos:

- `scan emits candidates sorted by refined score descending`
  - tres detecciones con scores `0.40f`, `0.91f`, `0.73f`
  - asercion final: ids ordenados `listOf(2L, 3L, 1L)`
- `scan reuses cached detections when cache is still valid`
  - asercion: contador de detecciones nuevas permanece en `0`
- `scan excludes items below review threshold`
  - con threshold `0.60f`, solo deben quedar ids con score `>= 0.60f`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Seguir el patron de `ScanQualityImagesUseCase`
- Leer `imageRepository.getImageCount(folders)`
- Procesar lotes con `getImagesBatch`
- Consultar cache valida por `dateModified`, `size`, `modelVersion`
- Ejecutar deteccion solo en faltantes
- Guardar nuevas detecciones
- Emitir `OverlayScanState` incremental
- Ordenar candidatos por `refinedScore DESC`, luego `overlayCoverageRatio DESC`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/domain/usecase/ScanOverlayCandidatesUseCase.kt app/src/test/java/com/duplicatefinder/domain/usecase/ScanOverlayCandidatesUseCaseTest.kt
git commit -m "feat(scan): add overlay candidate scan use case"
```

### Tarea 7: Implementar `EnsureOverlayModelBundleUseCase`

**Archivos:** crear `domain/usecase/EnsureOverlayModelBundleUseCase.kt`, crear `EnsureOverlayModelBundleUseCaseTest.kt`

**Paso 1 — Test que falla:**

Agregar tests:

- `returns current bundle when already available`
  - asercion: resultado `AVAILABLE`
- `downloads bundle when missing and download is allowed`
  - asercion: resultado `DOWNLOADED`
- `returns missing status when bundle url is not configured`
  - asercion: resultado `MISSING_CONFIGURATION`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.EnsureOverlayModelBundleUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Crear un resultado de uso simple:
  - `AVAILABLE`
  - `DOWNLOADED`
  - `MISSING_CONFIGURATION`
  - `FAILED`
- El caso de uso debe:
  - consultar bundle activo
  - si existe, devolverlo
  - si no existe y `allowDownload == true`, pedir descarga
  - si no hay URL configurada, devolver estado de configuracion faltante

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.EnsureOverlayModelBundleUseCaseTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/domain/usecase/EnsureOverlayModelBundleUseCase.kt app/src/test/java/com/duplicatefinder/domain/usecase/EnsureOverlayModelBundleUseCaseTest.kt
git commit -m "feat(domain): add overlay model bundle use case"
```

### Tarea 8: Implementar `GenerateOverlayPreviewUseCase`

**Archivos:** crear `domain/usecase/GenerateOverlayPreviewUseCase.kt`, crear `GenerateOverlayPreviewUseCaseTest.kt`

**Paso 1 — Test que falla:**

Agregar tests:

- `generate preview fails fast when bundle is unavailable`
  - asercion: el resultado es error controlado y el fake cleaning repository no recibe llamadas
- `generate preview delegates to cleaning repository when bundle is ready`
  - asercion: el fake cleaning repository recibe exactamente una llamada y retorna `CleaningPreview`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.GenerateOverlayPreviewUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Componer el caso de uso con:
  - `EnsureOverlayModelBundleUseCase`
  - `OverlayCleaningRepository`
- Flujo:
  1. resolver bundle
  2. si no hay bundle, retornar error controlado
  3. si existe, llamar `generatePreview`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.GenerateOverlayPreviewUseCaseTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/domain/usecase/GenerateOverlayPreviewUseCase.kt app/src/test/java/com/duplicatefinder/domain/usecase/GenerateOverlayPreviewUseCaseTest.kt
git commit -m "feat(domain): add overlay preview generation use case"
```

### Tarea 9: Implementar `ApplyOverlayPreviewDecisionUseCase`

**Archivos:** crear `domain/usecase/ApplyOverlayPreviewDecisionUseCase.kt`, crear `ApplyOverlayPreviewDecisionUseCaseTest.kt`

**Paso 1 — Test que falla:**

Agregar tests:

- `keep cleaned replaces original via cleaning repository`
  - asercion: el fake registra decision `KEEP_CLEANED_REPLACE_ORIGINAL`
- `delete all discards preview and moves original out of gallery`
  - asercion: el fake registra decision `DELETE_ALL`
- `skip keeps original and discards preview`
  - asercion: el fake registra decision `SKIP_KEEP_ORIGINAL`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ApplyOverlayPreviewDecisionUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Delegar toda la logica de decision final al `OverlayCleaningRepository`
- Garantizar que cada rama:
  - conserva limpia
  - elimina de galeria
  - descarta preview
  siga una sola ruta explicita y testeable

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ApplyOverlayPreviewDecisionUseCaseTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/domain/usecase/ApplyOverlayPreviewDecisionUseCase.kt app/src/test/java/com/duplicatefinder/domain/usecase/ApplyOverlayPreviewDecisionUseCaseTest.kt
git commit -m "feat(domain): add overlay preview decision use case"
```

## Fase 3: Implementaciones concretas y runtime

### Tarea 10: Implementar `OverlayRepositoryImpl`

**Archivos:** crear `data/repository/OverlayRepositoryImpl.kt`, modificar `RepositoryModule.kt`

**Paso 1 — Test que falla:**

Agregar en `ScanOverlayCandidatesUseCaseTest.kt` un escenario que valide serializacion de regiones y guardado/lectura de cache usando el contrato del repositorio concreto.

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Inyectar `OverlayDetectionDao`
- Implementar lectura de cache por lotes
- Validar cache por `size`, `dateModified`, `modelVersion`
- Serializar `overlayKinds` y `regionsJson` con `org.json`
- Dejar la deteccion concreta desacoplada detras de un metodo interno `detectOverlayCandidates`
- En `RepositoryModule`, bindear `OverlayRepositoryImpl`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/data/repository/OverlayRepositoryImpl.kt app/src/main/java/com/duplicatefinder/di/RepositoryModule.kt
git commit -m "feat(data): add overlay detection repository"
```

### Tarea 11: Implementar descarga y estado del bundle de modelos

**Archivos:** crear `data/repository/OverlayModelBundleRepositoryImpl.kt`, modificar `AppModule.kt`, modificar `AndroidManifest.xml`, modificar `app/build.gradle.kts`

**Paso 1 — Test que falla:**

En `EnsureOverlayModelBundleUseCaseTest.kt` agregar un caso con bundle inexistente y descarga permitida que espere una transicion a `DOWNLOADED`.

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.EnsureOverlayModelBundleUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Agregar dependencias:
  - `org.tensorflow:tensorflow-lite:2.16.1`
  - `org.tensorflow:tensorflow-lite-support:0.4.4`
  - `org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1`
- Agregar permiso `android.permission.INTERNET`
- Implementar `OverlayModelBundleRepositoryImpl`:
  - carpeta `filesDir/overlay_models/current/`
  - manifest local `bundle.json`
  - chequeo de integridad basico por existencia de archivos definidos
  - descarga por `HttpURLConnection`
- En `AppModule`, proveer la URL configurable del manifest y el directorio del bundle

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.EnsureOverlayModelBundleUseCaseTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/duplicatefinder/data/repository/OverlayModelBundleRepositoryImpl.kt app/src/main/java/com/duplicatefinder/di/AppModule.kt
git commit -m "feat(app): add overlay model bundle download support"
```

### Tarea 12: Implementar preview y aplicacion final de limpieza

**Archivos:** crear `data/repository/OverlayCleaningRepositoryImpl.kt`, completar `GenerateOverlayPreviewUseCaseTest.kt`, completar `ApplyOverlayPreviewDecisionUseCaseTest.kt`

**Paso 1 — Test que falla:**

Agregar un escenario de fallo de reemplazo y uno de `skip`:

- `replace failure keeps original and removes partial preview`
  - asercion: la original sigue marcada como disponible y el preview temporal queda eliminado
- `skip removes temporary preview file`
  - asercion: el archivo temporal deja de existir despues de ejecutar `SKIP_KEEP_ORIGINAL`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.GenerateOverlayPreviewUseCaseTest" --tests "com.duplicatefinder.domain.usecase.ApplyOverlayPreviewDecisionUseCaseTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Generar preview temporal en `cacheDir/overlay_preview/`
- Ejecutar inpainting con TFLite sobre bitmap downsampled
- Implementar rutas de decision:
  - `KEEP_CLEANED_REPLACE_ORIGINAL`
  - `DELETE_ALL`
  - `SKIP_KEEP_ORIGINAL`
- Reglas:
  - nunca tocar la original antes del preview
  - si falla el reemplazo, conservar original
  - limpiar preview temporal siempre

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.domain.usecase.GenerateOverlayPreviewUseCaseTest" --tests "com.duplicatefinder.domain.usecase.ApplyOverlayPreviewDecisionUseCaseTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/data/repository/OverlayCleaningRepositoryImpl.kt app/src/test/java/com/duplicatefinder/domain/usecase/GenerateOverlayPreviewUseCaseTest.kt app/src/test/java/com/duplicatefinder/domain/usecase/ApplyOverlayPreviewDecisionUseCaseTest.kt
git commit -m "feat(trash): add overlay preview apply and rollback flow"
```

## Fase 4: ViewModel y UI

### Tarea 13: Implementar `OverlayReviewViewModel`

**Archivos:** crear `presentation/screens/overlay/OverlayReviewViewModel.kt`, crear `OverlayReviewViewModelTest.kt`

**Paso 1 — Test que falla:**

Agregar tests:

- `start review requires selected folders`
  - asercion: `requiresFolderSelection == true`
- `remove watermark generates preview for current item`
  - asercion: `previewState?.status == PreviewStatus.READY`
- `skip preview advances keeping original`
  - asercion: el id actual entra en `skippedPreviewIds` y cambia `currentIndex`
- `confirm cleaned replacement advances to next item`
  - asercion: el id actual entra en `completedCleanReplaceIds` y cambia `currentIndex`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewViewModelTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Seguir la forma de `QualityReviewViewModel`
- Integrar:
  - `ScanOverlayCandidatesUseCase`
  - `GenerateOverlayPreviewUseCase`
  - `ApplyOverlayPreviewDecisionUseCase`
  - `MoveToTrashUseCase` solo para la accion `Eliminar`
- Exponer acciones:
  - `keepCurrent()`
  - `markCurrentForTrash()`
  - `generatePreviewForCurrent()`
  - `keepCleanedPreview()`
  - `deleteAllFromPreview()`
  - `skipPreview()`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewViewModelTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModel.kt app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModelTest.kt
git commit -m "feat(scan): add overlay review view model"
```

### Tarea 14: Implementar pantalla de revision de overlays

**Archivos:** crear `presentation/screens/overlay/OverlayReviewScreen.kt`, modificar `strings.xml`

**Paso 1 — Test que falla:**

Usar `OverlayReviewViewModelTest.kt` para exigir strings y callbacks:

- validar que exista `R.string.overlay_title`
- validar que el texto resuelto para ese recurso sea `Review Watermarks`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewViewModelTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- Reutilizar el layout base de `QualityReviewScreen`
- Mostrar:
  - score de overlay
  - tipos detectados
  - acciones `Keep`, `Mark for Trash`, `Remove Watermark`
- Mostrar preview cuando `previewState` este `READY`
- Agregar strings:
  - `overlay_title`
  - `overlay_filter_title`
  - `overlay_review_progress`
  - `overlay_remove_watermark`
  - `overlay_preview_keep_cleaned`
  - `overlay_preview_delete_all`
  - `overlay_preview_skip`
  - `overlay_no_results_title`
  - `overlay_no_results_subtitle`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewViewModelTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(ui): add overlay review screen"
```

### Tarea 15: Integrar entrada desde Home y navegacion

**Archivos:** modificar `HomeScreen.kt`, modificar `NavGraph.kt`, modificar `strings.xml`

**Paso 1 — Test que falla:**

Agregar un test simple de compilacion a `OverlayReviewViewModelTest.kt` que use la nueva ruta `overlay_review`.

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewViewModelTest"
```

Resultado esperado:

- `BUILD FAILED`

**Paso 3 — Implementar:**

- En `HomeScreen`, agregar CTA `Review Watermarks`
- En `NavGraph`, agregar:
  - `Screen.Overlay`
  - `composable(Screen.Overlay.route)`
- Mantener `Overlay` fuera del bottom bar, igual que calidad y resolucion

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests "com.duplicatefinder.presentation.screens.overlay.OverlayReviewViewModelTest"
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/presentation/screens/home/HomeScreen.kt app/src/main/java/com/duplicatefinder/presentation/navigation/NavGraph.kt app/src/main/res/values/strings.xml
git commit -m "feat(home): add watermark review navigation"
```

## Fase 5: Verificacion completa

### Tarea 16: Ejecutar suite completa y validacion manual

**Archivos:** sin cambios de codigo

**Paso 1 — Ejecutar tests completos:**

```bash
./gradlew :app:testDebugUnitTest
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 2 — Validar compilacion de app debug:**

```bash
./gradlew :app:assembleDebug
```

Resultado esperado:

- `BUILD SUCCESSFUL`

**Paso 3 — Validacion manual en dispositivo objetivo:**

Checklist:

- Entrar a `Review Watermarks` con carpetas seleccionadas
- Confirmar ranking descendente por score
- Generar preview de al menos 3 imagenes
- Confirmar `Conservar limpia`
- Confirmar `Eliminar ambas`
- Confirmar `Skip`
- Reabrir pantalla y validar limpieza de previews temporales
- Activar modo avion y verificar funcionamiento offline despues de descargar bundle

**Paso 4 — Commit final de verificacion si se agregan notas o fixes menores:**

```bash
git add .
git commit -m "test(app): verify overlay watermark review flow"
```

Solo hacer este commit si hubo cambios reales derivados de la verificacion.

## Auto-revision del plan

- [x] Cada requerimiento de la spec tiene al menos una tarea
- [x] No hay huecos ni abreviaciones pendientes dentro del plan
- [x] Los tipos y nombres son consistentes con el repo actual
- [x] El orden de ejecucion va de contratos a data, luego ViewModel y UI
- [x] El plan evita implementar sobre `main`

## Riesgo pendiente antes de ejecutar

Hay un unico insumo externo fuera del repo que debe existir antes de probar la descarga real en dispositivo:

- URL valida del manifest del bundle de modelos

Ese riesgo no bloquea el trabajo de dominio, cache, UI, tests ni la integracion base.

## Transicion

Plan listo con 16 tareas. Como quieres ejecutarlo?

- `a)` Con subagentes (recomendado) — usa `$execute`
- `b)` Paso a paso en esta sesion
- `c)` Revisar o ajustar el plan primero
