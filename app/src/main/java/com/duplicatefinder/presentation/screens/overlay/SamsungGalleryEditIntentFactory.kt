package com.duplicatefinder.presentation.screens.overlay

import android.content.Intent
import com.duplicatefinder.domain.model.ImageItem

data class SamsungGalleryEditAvailability(
    val enabled: Boolean,
    val helperText: String? = null
)

class SamsungGalleryEditIntentFactory(
    private val deviceManufacturer: String,
    private val requiredMessage: String,
    private val advisoryMessage: String,
    private val canResolveEditIntent: (Intent) -> Boolean
) {

    fun availabilityFor(image: ImageItem): SamsungGalleryEditAvailability {
        if (!deviceManufacturer.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)) {
            return SamsungGalleryEditAvailability(
                enabled = false,
                helperText = requiredMessage
            )
        }

        return if (canResolveEditIntent(createIntent(image))) {
            SamsungGalleryEditAvailability(
                enabled = true,
                helperText = advisoryMessage
            )
        } else {
            SamsungGalleryEditAvailability(
                enabled = false,
                helperText = requiredMessage
            )
        }
    }

    fun createIntent(image: ImageItem): Intent {
        return Intent(Intent.ACTION_EDIT)
            .setDataAndType(image.uri, image.mimeType)
            .setPackage(SAMSUNG_GALLERY_PACKAGE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    companion object {
        const val SAMSUNG_GALLERY_PACKAGE = "com.sec.android.gallery3d"

        private const val SAMSUNG_MANUFACTURER = "samsung"
    }
}
