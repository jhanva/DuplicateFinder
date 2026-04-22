# Watermark Review Migration Plan

## Precondición

Este plan asume `JDK 17`. Hoy en este workspace `./gradlew` falla porque no hay Java, así que los comandos de verificación de abajo son los que debes ejecutar localmente cuando implementes.

```bash
java -version
./gradlew -version
```

Output esperado: JVM `17.x` y Gradle operativo.

## Mapa de archivos

### Crear

- `app/src/main/java/com/duplicatefinder/presentation/screens/overlay/SamsungGalleryEditIntentFactory.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayExternalEditSession.kt`
- `app/src/test/java/com/duplicatefinder/presentation/screens/overlay/SamsungGalleryEditIntentFactoryTest.kt`

### Modificar

- `app/src/main/java/com/duplicatefinder/di/AppModule.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiState.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModel.kt`
- `app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiStateTest.kt`
- `app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModelTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/TestFixtures.kt`
- `app/src/main/java/com/duplicatefinder/di/RepositoryModule.kt`
- `app/build.gradle.kts`
- `app/src/main/java/com/duplicatefinder/domain/repository/OverlayModelBundleRepository.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayModelBundleRepositoryImpl.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayOnnxRuntime.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayOnnxPostProcessor.kt`
- `app/src/test/java/com/duplicatefinder/data/repository/OverlayModelBundleRepositoryImplTest.kt`
- `app/src/test/java/com/duplicatefinder/data/repository/OverlayRepositoryImplTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/ScanOverlayCandidatesUseCaseTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/EnsureOverlayModelBundleUseCaseTest.kt`

### Eliminar

- `app/src/main/java/com/duplicatefinder/domain/model/CleaningPreview.kt`
- `app/src/main/java/com/duplicatefinder/domain/model/OverlayCleaningSupport.kt`
- `app/src/main/java/com/duplicatefinder/domain/repository/OverlayCleaningRepository.kt`
- `app/src/main/java/com/duplicatefinder/domain/repository/OverlayCleaningModelRepository.kt`
- `app/src/main/java/com/duplicatefinder/domain/usecase/GenerateOverlayPreviewUseCase.kt`
- `app/src/main/java/com/duplicatefinder/domain/usecase/ApplyOverlayPreviewDecisionUseCase.kt`
- `app/src/main/java/com/duplicatefinder/domain/usecase/EnsureOverlayCleaningModelUseCase.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayCleaningRepositoryImpl.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayCleaningModelRepositoryImpl.kt`
- `app/src/main/java/com/duplicatefinder/data/repository/OverlayCleaningBundledModelSource.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/GenerateOverlayPreviewUseCaseTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/ApplyOverlayPreviewDecisionUseCaseTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/usecase/EnsureOverlayCleaningModelUseCaseTest.kt`
- `app/src/test/java/com/duplicatefinder/domain/model/OverlayCleaningSupportTest.kt`
- `app/src/test/java/com/duplicatefinder/data/repository/OverlayCleaningRepositoryImplTest.kt`
- `app/src/test/java/com/duplicatefinder/data/repository/OverlayCleaningModelRepositoryImplTest.kt`

## Plan

### Tarea 1: Introducir el contrato Samsung Gallery

**Archivos:** crear `SamsungGalleryEditIntentFactory.kt`, crear `SamsungGalleryEditIntentFactoryTest.kt`

**Paso 1 — Test que falla:**
Agregar estos tests nuevos:

```kotlin
@Test
fun `availability is enabled only for samsung gallery on samsung devices`() {
    val factory = SamsungGalleryEditIntentFactory(
        deviceManufacturer = "samsung",
        canResolveEditIntent = { true }
    )

    val availability = factory.availabilityFor(testImage(id = 1, size = 100))

    assertTrue(availability.enabled)
    assertEquals(null, availability.reason)
}

@Test
fun `availability is disabled on non samsung devices`() {
    val factory = SamsungGalleryEditIntentFactory(
        deviceManufacturer = "google",
        canResolveEditIntent = { true }
    )

    val availability = factory.availabilityFor(testImage(id = 1, size = 100))

    assertFalse(availability.enabled)
    assertEquals(
        "Requires Samsung Gallery AI editing on a supported Samsung device.",
        availability.reason
    )
}

@Test
fun `create intent targets samsung gallery with edit permissions`() {
    val image = testImage(id = 1, size = 100)
    val factory = SamsungGalleryEditIntentFactory(
        deviceManufacturer = "samsung",
        canResolveEditIntent = { true }
    )

    val intent = factory.createIntent(image)

    assertEquals(Intent.ACTION_EDIT, intent.action)
    assertEquals(image.uri, intent.data)
    assertEquals(image.mimeType, intent.type)
    assertEquals("com.sec.android.gallery3d", intent.`package`)
    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
}
```

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.duplicatefinder.presentation.screens.overlay.SamsungGalleryEditIntentFactoryTest'
```

Output esperado: errores de compilación por `Unresolved reference: SamsungGalleryEditIntentFactory`.

**Paso 3 — Implementar:**

Crear una clase pura con esta API exacta:

- `data class SamsungGalleryEditAvailability(val enabled: Boolean, val reason: String? = null)`
- `class SamsungGalleryEditIntentFactory(...)`
- `fun availabilityFor(image: ImageItem): SamsungGalleryEditAvailability`
- `fun createIntent(image: ImageItem): Intent`
- constante de package: `"com.sec.android.gallery3d"`
- mensaje fijo: `"Requires Samsung Gallery AI editing on a supported Samsung device."`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.duplicatefinder.presentation.screens.overlay.SamsungGalleryEditIntentFactoryTest'
```

Output esperado: 3 tests `PASSED` y `BUILD SUCCESSFUL`.

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/presentation/screens/overlay/SamsungGalleryEditIntentFactory.kt app/src/test/java/com/duplicatefinder/presentation/screens/overlay/SamsungGalleryEditIntentFactoryTest.kt
git commit -m "feat(overlay): add samsung gallery edit contract"
```

### Tarea 2: Migrar el review a edición externa Samsung

**Archivos:** crear `OverlayExternalEditSession.kt`; modificar `OverlayReviewUiState.kt`, `OverlayReviewViewModel.kt`, `OverlayReviewScreen.kt`, `AppModule.kt`, `strings.xml`, `OverlayReviewUiStateTest.kt`, `OverlayReviewViewModelTest.kt`

**Paso 1 — Test que falla:**

Reemplazar los tests de preview en `OverlayReviewViewModelTest.kt` y `OverlayReviewUiStateTest.kt` por estos comportamientos:

- `open in samsung gallery queues external edit intent and snapshots metadata`
- `returning from samsung gallery with changed metadata marks image edited and advances`
- `returning from samsung gallery without metadata changes keeps current item pending`
- `reviewed count includes edited in gallery ids`

**Paso 2 — Verificar que falla:**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.duplicatefinder.presentation.screens.overlay.OverlayReviewViewModelTest' --tests 'com.duplicatefinder.presentation.screens.overlay.OverlayReviewUiStateTest'
```

Output esperado: fallos por campos y métodos no existentes como `editedInGalleryIds`, `openCurrentInSamsungGallery`, `onExternalEditorResult`.

**Paso 3 — Implementar:**

Aplicar este slice vertical en una sola pasada:

- crear `OverlayExternalEditSession` con `imageId`, `originalSize`, `originalDateModified`, `startedAt`
- `OverlayReviewUiState`:
  - agregar `editedInGalleryIds: Set<Long>`
  - agregar `externalEditSession: OverlayExternalEditSession?`
  - agregar `pendingExternalEditIntent: Intent?`
  - agregar `samsungGalleryDisabledReason: String?`
  - eliminar `previewState`, `isGeneratingPreview`, `cleaningRequestedIds`, `completedCleanReplaceIds`, `skippedPreviewIds`, `pendingPreviewDeleteConfirmation`
  - hacer que `reviewedCount` incluya `editedInGalleryIds`
- `AppModule`:
  - proveer `SamsungGalleryEditIntentFactory` usando `Build.MANUFACTURER` y `intent.resolveActivity(context.packageManager) != null`
- `OverlayReviewViewModel`:
  - quitar `GenerateOverlayPreviewUseCase` y `ApplyOverlayPreviewDecisionUseCase` del constructor
  - agregar `openCurrentInSamsungGallery()`
  - agregar `onExternalEditorLaunchConsumed()`
  - agregar `onExternalEditorResult()`
  - al abrir: snapshot de `size` y `dateModified`, set de `pendingExternalEditIntent`
  - al volver: recargar con `imageRepository.getImageById`, comparar metadata, marcar `editedInGalleryIds` solo si cambió
- `OverlayReviewScreen`:
  - reemplazar `Remove Watermark` por `Open in Samsung Gallery`
  - usar `rememberLauncherForActivityResult(StartActivityForResult())`
  - lanzar cuando `pendingExternalEditIntent` no sea `null`
  - quitar toda la UI de preview side-by-side
  - dejar el botón visible pero disabled con helper text si Samsung no está disponible
  - hacer summary propio en overlay para incluir `Edited in Samsung Gallery`
- `strings.xml`:
  - renombrar `overlay_remove_watermark`
  - agregar `overlay_samsung_gallery_required`
  - agregar `overlay_no_gallery_changes`
  - agregar `overlay_summary_edited`

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.duplicatefinder.presentation.screens.overlay.SamsungGalleryEditIntentFactoryTest' --tests 'com.duplicatefinder.presentation.screens.overlay.OverlayReviewViewModelTest' --tests 'com.duplicatefinder.presentation.screens.overlay.OverlayReviewUiStateTest'
./gradlew :app:compileDebugKotlin
```

Output esperado: tests verdes y `BUILD SUCCESSFUL`.

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayExternalEditSession.kt app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiState.kt app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModel.kt app/src/main/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewScreen.kt app/src/main/java/com/duplicatefinder/di/AppModule.kt app/src/main/res/values/strings.xml app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewUiStateTest.kt app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModelTest.kt
git commit -m "feat(overlay): hand off watermark edits to samsung gallery"
```

### Tarea 3: Borrar el subsistema completo de cleaning interno

**Archivos:** eliminar todos los archivos de cleaning listados arriba; modificar `TestFixtures.kt`, `RepositoryModule.kt`, `AppModule.kt`, `build.gradle.kts`

**Paso 1 — Test que falla:**

Usar búsqueda de símbolos prohibidos como guardrail de cleanup:

```bash
rg -n "CleaningPreview|PreviewStatus|OverlayPreviewDecision|GenerateOverlayPreviewUseCase|ApplyOverlayPreviewDecisionUseCase|EnsureOverlayCleaningModelUseCase|OverlayCleaningRepository|OverlayCleaningModelRepository|OverlayCleaningRepositoryImpl|OverlayCleaningModelRepositoryImpl|OverlayCleaningBundledModelSource|OVERLAY_CLEANING_MODEL_URL|overlayPreviewDir" app/src/main/java app/src/test/java app/build.gradle.kts
```

**Paso 2 — Verificar que falla:**

Output esperado: múltiples matches en dominio, data, tests, DI y Gradle.

**Paso 3 — Implementar:**

Hacer el borrado coordinado para dejar la build verde:

- borrar todos los archivos de `cleaning` en `domain/`, `data/` y `test/`
- `TestFixtures.kt`: quitar `testCleaningPreview`, `BaseOverlayCleaningRepositoryFake`, `BaseOverlayCleaningModelRepositoryFake` y sus imports
- `RepositoryModule.kt`: quitar bindings de cleaning
- `AppModule.kt`: quitar providers `overlayCleaningModelUrl`, `overlayCleaningModelDir`, `overlayPreviewDir`, `OverlayCleaningBundledModelSource`
- `build.gradle.kts`:
  - quitar `overlayCleaningModelUrl`
  - quitar task `prepareOverlayCleaningModelAsset`
  - quitar `OVERLAY_CLEANING_MODEL_URL`
  - quitar `sourceSets.getByName("main").assets.srcDir(overlayCleaningGeneratedAssetsDir)`
  - mantener `onnxruntime-android`, porque la detección sigue usando ONNX

**Paso 4 — Verificar que pasa:**

```bash
rg -n "CleaningPreview|PreviewStatus|OverlayPreviewDecision|GenerateOverlayPreviewUseCase|ApplyOverlayPreviewDecisionUseCase|EnsureOverlayCleaningModelUseCase|OverlayCleaningRepository|OverlayCleaningModelRepository|OverlayCleaningRepositoryImpl|OverlayCleaningModelRepositoryImpl|OverlayCleaningBundledModelSource|OVERLAY_CLEANING_MODEL_URL|overlayPreviewDir" app/src/main/java app/src/test/java app/build.gradle.kts
./gradlew :app:compileDebugKotlin
```

Output esperado:

- `rg` sin output y exit code `1`
- compilación con `BUILD SUCCESSFUL`

**Paso 5 — Commit:**

```bash
git add -u app/src/main/java app/src/test/java app/build.gradle.kts
git add app/src/test/java/com/duplicatefinder/domain/TestFixtures.kt app/src/main/java/com/duplicatefinder/di/RepositoryModule.kt app/src/main/java/com/duplicatefinder/di/AppModule.kt
git commit -m "refactor(overlay): remove internal watermark cleaning subsystem"
```

### Tarea 4: Sacar el inpainter del bundle de detección y del runtime

**Archivos:** modificar `OverlayModelBundleRepository.kt`, `OverlayModelBundleRepositoryImpl.kt`, `OverlayOnnxRuntime.kt`, `OverlayOnnxPostProcessor.kt`, `OverlayModelBundleRepositoryImplTest.kt`, `OverlayRepositoryImplTest.kt`, `ScanOverlayCandidatesUseCaseTest.kt`, `EnsureOverlayModelBundleUseCaseTest.kt`, `OverlayReviewViewModelTest.kt`

**Paso 1 — Test que falla:**

Agregar o ajustar este test de contrato:

- `download bundle ignores optional inpainter asset and activates detector-only bundle`

Y usar este guardrail:

```bash
rg -n "inpainterPath|inputSizeInpainter|OverlayOnnxInpainterContract|onnx\\.inpainter|fun inpaint\\(|buildMask\\(" app/src/main/java app/src/test/java
```

**Paso 2 — Verificar que falla:**

Output esperado:

- el test falla porque `requiredAssetPaths` todavía exige el inpainter
- `rg` muestra referencias en contrato, repo y runtime

**Paso 3 — Implementar:**

- `OverlayModelBundleRepository.kt`:
  - borrar `OverlayOnnxInpainterContract`
  - borrar `inpainter` de `OverlayOnnxRuntimeContract`
  - borrar `inpainterPath` e `inputSizeInpainter` de `OverlayModelBundleInfo`
  - dejar `requiredAssetPaths` solo con detector + mask refiner
- `OverlayModelBundleRepositoryImpl.kt`:
  - dejar de leer `inpainterPath` como obligatorio
  - ignorar `inpainter` si viene en el JSON
  - descargar solo assets de detección
- `OverlayOnnxRuntime.kt`:
  - borrar `inpaint(...)`
  - borrar `createMaskTensor`, `createConcatenatedImageMaskTensor`, `tensorToBitmap`, `denormalizeColor` y cualquier helper exclusivo de cleaning
- `OverlayOnnxPostProcessor.kt`:
  - borrar `buildMask(...)`
- actualizar builders de test para `OverlayModelBundleInfo` sin campos de inpainter

**Paso 4 — Verificar que pasa:**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.duplicatefinder.data.repository.OverlayModelBundleRepositoryImplTest' --tests 'com.duplicatefinder.data.repository.OverlayRepositoryImplTest' --tests 'com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCaseTest' --tests 'com.duplicatefinder.domain.usecase.EnsureOverlayModelBundleUseCaseTest'
rg -n "inpainterPath|inputSizeInpainter|OverlayOnnxInpainterContract|onnx\\.inpainter|fun inpaint\\(|buildMask\\(" app/src/main/java app/src/test/java
```

Output esperado:

- tests verdes
- `rg` sin output y exit code `1`

**Paso 5 — Commit:**

```bash
git add app/src/main/java/com/duplicatefinder/domain/repository/OverlayModelBundleRepository.kt app/src/main/java/com/duplicatefinder/data/repository/OverlayModelBundleRepositoryImpl.kt app/src/main/java/com/duplicatefinder/data/repository/OverlayOnnxRuntime.kt app/src/main/java/com/duplicatefinder/data/repository/OverlayOnnxPostProcessor.kt app/src/test/java/com/duplicatefinder/data/repository/OverlayModelBundleRepositoryImplTest.kt app/src/test/java/com/duplicatefinder/data/repository/OverlayRepositoryImplTest.kt app/src/test/java/com/duplicatefinder/domain/usecase/ScanOverlayCandidatesUseCaseTest.kt app/src/test/java/com/duplicatefinder/domain/usecase/EnsureOverlayModelBundleUseCaseTest.kt app/src/test/java/com/duplicatefinder/presentation/screens/overlay/OverlayReviewViewModelTest.kt
git commit -m "refactor(overlay): drop inpainter from detection bundle"
```

### Tarea 5: Verificación final y QA en dispositivo

**Archivos:** sin cambios de código

**Paso 1 — Verificación fresca:**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Output esperado: suite verde, APK generado, instalación exitosa.

**Paso 2 — QA manual en S23 Ultra:**

1. Abrir `Review Watermarks`.
2. Confirmar que aparece `Open in Samsung Gallery`.
3. Tocar el botón y validar que abre Samsung Gallery.
4. Entrar manualmente a `Object Eraser`, guardar sobre el original y volver.
5. Confirmar que el item avanza y cuenta en `Edited in Samsung Gallery`.
6. Repetir sin guardar cambios.
7. Confirmar que el item no avanza y aparece el mensaje `No changes were detected in Samsung Gallery.`
8. Validar que `Keep` y `Mark for Trash` siguen funcionando.

**Paso 3 — QA de no compatibilidad:**

- Ejecutar en un dispositivo/emulador no Samsung.
- Confirmar que el botón está visible pero disabled.
- Confirmar helper text: `Requires Samsung Gallery AI editing on a supported Samsung device.`

**Paso 4 — Commit de cierre:**

No hacer commit extra si todo quedó cubierto por las tareas 1-4.

## Auto-revisión

- [x] La spec aprobada queda cubierta
- [x] El cleaning ML se elimina del flujo y del build
- [x] La detección de watermarks se mantiene
- [x] El handoff a Samsung Gallery queda testeable
- [x] El modelo inpainter deja de descargarse y de existir en runtime

## Transición

Plan listo con 5 tareas.

Opciones de ejecución:

- `a)` Con subagentes (recomendado) — usa `$execute`
- `b)` Paso a paso en esta sesión
- `c)` Revisar/ajustar el plan primero
