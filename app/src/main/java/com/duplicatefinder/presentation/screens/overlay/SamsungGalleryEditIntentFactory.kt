package com.duplicatefinder.presentation.screens.overlay

import android.content.Intent
import com.duplicatefinder.domain.model.ImageItem

data class SamsungGalleryEditAvailability(
    val enabled: Boolean,
    val reason: String? = null
)

class SamsungGalleryEditIntentFactory(
    private val deviceManufacturer: String,
    private val canResolveEditIntent: (Intent) -> Boolean
) {

    fun availabilityFor(image: ImageItem): SamsungGalleryEditAvailability {
        if (!deviceManufacturer.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)) {
            return SamsungGalleryEditAvailability(
                enabled = false,
                reason = REQUIRES_SAMSUNG_GALLERY_REASON
            )
        }

        return if (canResolveEditIntent(createIntent(image))) {
            SamsungGalleryEditAvailability(enabled = true)
        } else {
            SamsungGalleryEditAvailability(
                enabled = false,
                reason = REQUIRES_SAMSUNG_GALLERY_REASON
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
        const val REQUIRES_SAMSUNG_GALLERY_REASON =
            "Requires Samsung Gallery AI editing on a supported Samsung device."

        private const val SAMSUNG_MANUFACTURER = "samsung"
    }
}
