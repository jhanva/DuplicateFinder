package com.duplicatefinder.presentation.screens.overlay

import android.content.Intent
import com.duplicatefinder.domain.model.ImageItem

data class SamsungGalleryEditAvailability(
    val enabled: Boolean,
    val helperText: String? = null
)

data class SamsungGalleryLaunchSpec(
    val action: String,
    val mimeType: String?,
    val className: String? = null
) {
    fun toIntent(image: ImageItem): Intent {
        val intent = Intent(action)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        if (className.isNullOrBlank()) {
            intent.setPackage(SamsungGalleryEditIntentFactory.SAMSUNG_GALLERY_PACKAGE)
        } else {
            intent.setClassName(
                SamsungGalleryEditIntentFactory.SAMSUNG_GALLERY_PACKAGE,
                className
            )
        }

        return if (mimeType.isNullOrBlank()) {
            intent.setData(image.uri)
        } else {
            intent.setDataAndType(image.uri, mimeType)
        }
    }
}

data class SamsungGalleryLaunchRequest(
    val image: ImageItem,
    val specs: List<SamsungGalleryLaunchSpec>
)

class SamsungGalleryEditIntentFactory(
    private val deviceManufacturer: String,
    private val requiredMessage: String,
    private val advisoryMessage: String,
    private val isSamsungGalleryInstalled: () -> Boolean
) {

    fun availabilityFor(@Suppress("UNUSED_PARAMETER") image: ImageItem): SamsungGalleryEditAvailability {
        if (!deviceManufacturer.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)) {
            return SamsungGalleryEditAvailability(
                enabled = false,
                helperText = requiredMessage
            )
        }

        if (!isSamsungGalleryInstalled()) {
            return SamsungGalleryEditAvailability(
                enabled = false,
                helperText = requiredMessage
            )
        }

        return SamsungGalleryEditAvailability(
            enabled = true,
            helperText = advisoryMessage
        )
    }

    fun createLaunchRequest(image: ImageItem): SamsungGalleryLaunchRequest {
        return SamsungGalleryLaunchRequest(
            image = image,
            specs = createLaunchSpecs(image)
        )
    }

    fun createLaunchSpecs(image: ImageItem): List<SamsungGalleryLaunchSpec> {
        return listOf(
            SamsungGalleryLaunchSpec(Intent.ACTION_VIEW, image.mimeType, MODERN_GALLERY_ACTIVITY),
            SamsungGalleryLaunchSpec(Intent.ACTION_VIEW, GENERIC_IMAGE_MIME_TYPE, MODERN_GALLERY_ACTIVITY),
            SamsungGalleryLaunchSpec(Intent.ACTION_VIEW, image.mimeType, OPAQUE_GALLERY_ACTIVITY),
            SamsungGalleryLaunchSpec(Intent.ACTION_VIEW, GENERIC_IMAGE_MIME_TYPE, OPAQUE_GALLERY_ACTIVITY),
            SamsungGalleryLaunchSpec(Intent.ACTION_VIEW, image.mimeType, LEGACY_GALLERY_ACTIVITY),
            SamsungGalleryLaunchSpec(Intent.ACTION_VIEW, GENERIC_IMAGE_MIME_TYPE, LEGACY_GALLERY_ACTIVITY),
            SamsungGalleryLaunchSpec(Intent.ACTION_VIEW, image.mimeType),
            SamsungGalleryLaunchSpec(Intent.ACTION_VIEW, GENERIC_IMAGE_MIME_TYPE)
        ).distinctBy { spec ->
            Triple(spec.action, spec.mimeType, spec.className)
        }
    }

    companion object {
        const val SAMSUNG_GALLERY_PACKAGE = "com.sec.android.gallery3d"
        private const val GENERIC_IMAGE_MIME_TYPE = "image/*"
        private const val MODERN_GALLERY_ACTIVITY = "com.samsung.android.gallery.app.activity.GalleryActivity"
        private const val OPAQUE_GALLERY_ACTIVITY = "com.sec.android.gallery3d.app.GalleryOpaqueActivity"
        private const val LEGACY_GALLERY_ACTIVITY = "com.sec.android.gallery3d.app.Gallery"

        private const val SAMSUNG_MANUFACTURER = "samsung"
    }
}
