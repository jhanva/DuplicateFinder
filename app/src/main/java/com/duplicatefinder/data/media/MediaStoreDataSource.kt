package com.duplicatefinder.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.database.Cursor
import com.duplicatefinder.domain.model.ImageItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    suspend fun getAllImages(folders: Set<String> = emptySet()): List<ImageItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageItem>()

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val (selection, selectionArgs) = buildFolderSelection(folders)

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                images.add(cursor.toImageItem())
            }
        }

        images
    }

    suspend fun getImagesBatch(
        folders: Set<String> = emptySet(),
        limit: Int,
        offset: Int
    ): List<ImageItem> = withContext(Dispatchers.IO) {
        if (limit <= 0 || offset < 0) return@withContext emptyList()

        val images = mutableListOf<ImageItem>()
        val (selection, selectionArgs) = buildFolderSelection(folders)
        val args = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Images.Media.DATE_MODIFIED)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            if (!selection.isNullOrBlank() && selectionArgs != null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
        }

        contentResolver.query(collection, projection, args, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                images.add(cursor.toImageItem())
            }
        }

        images
    }

    suspend fun getImageById(id: Long): ImageItem? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(
            collection,
            id
        )

        contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val relativePathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val dataPathColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val folderColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null
                val absolutePath = if (dataPathColumn >= 0) cursor.getString(dataPathColumn) else null
                val folderName = cursor.getString(folderColumn) ?: ""
                val path = when {
                    !absolutePath.isNullOrBlank() -> absolutePath
                    !relativePath.isNullOrBlank() -> "$relativePath$name"
                    else -> folderName
                }

                ImageItem(
                    id = id,
                    uri = uri,
                    path = path,
                    name = name,
                    size = cursor.getLong(sizeColumn),
                    dateModified = cursor.getLong(dateColumn),
                    mimeType = cursor.getString(mimeColumn) ?: "image/*",
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    folderName = folderName
                )
            } else null
        }
    }

    suspend fun getFolders(): List<String> = withContext(Dispatchers.IO) {
        val folders = mutableSetOf<String>()

        contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val folderColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                cursor.getString(folderColumn)?.let { folders.add(it) }
            }
        }

        folders.toList().sorted()
    }

    suspend fun getImageCount(folders: Set<String> = emptySet()): Int = withContext(Dispatchers.IO) {
        val (selection, selectionArgs) = buildFolderSelection(folders)
        contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            null
        )?.use { it.count } ?: 0
    }

    private fun buildFolderSelection(folders: Set<String>): Pair<String?, Array<String>?> {
        if (folders.isEmpty()) return null to null

        val placeholders = folders.joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} IN ($placeholders)"
        return selection to folders.toTypedArray()
    }

    private fun Cursor.toImageItem(): ImageItem {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        val name = getString(getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "Unknown"
        val relativePathColumn = getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
        val dataPathColumn = getColumnIndex(MediaStore.Images.Media.DATA)
        val relativePath = if (relativePathColumn >= 0) getString(relativePathColumn) else null
        val absolutePath = if (dataPathColumn >= 0) getString(dataPathColumn) else null
        val folderName = getString(getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)) ?: ""
        val path = when {
            !absolutePath.isNullOrBlank() -> absolutePath
            !relativePath.isNullOrBlank() -> "$relativePath$name"
            else -> folderName
        }

        return ImageItem(
            id = id,
            uri = ContentUris.withAppendedId(collection, id),
            path = path,
            name = name,
            size = getLong(getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
            dateModified = getLong(getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)),
            mimeType = getString(getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: "image/*",
            width = getInt(getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)),
            height = getInt(getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)),
            folderName = folderName
        )
    }
}
