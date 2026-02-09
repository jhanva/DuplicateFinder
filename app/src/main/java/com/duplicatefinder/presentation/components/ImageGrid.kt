package com.duplicatefinder.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duplicatefinder.domain.model.ImageItem

@Composable
fun ImageGrid(
    images: List<ImageItem>,
    selectedImages: Set<Long>,
    onImageClick: (ImageItem) -> Unit,
    onImageLongClick: (ImageItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = images,
            key = { it.id }
        ) { image ->
            ImageCard(
                image = image,
                isSelected = image.id in selectedImages,
                onClick = { onImageClick(image) },
                onLongClick = { onImageLongClick(image) }
            )
        }
    }
}
