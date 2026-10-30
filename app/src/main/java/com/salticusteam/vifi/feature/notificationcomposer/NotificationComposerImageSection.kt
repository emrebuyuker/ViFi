package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.salticusteam.vifi.R

/**
 * An optional image shown on the notification (the push and the inbox card), iOS `NotificationComposerImageSection`:
 * picked with the system Photo Picker (no storage permission), downsampled and encoded off the main thread as a JPEG
 * of at most 1 MB, previewed, and uploaded only when the notification is sent.
 */
@Composable
internal fun ImageSection(
    state: NotificationComposerUiState,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    val enabled = !state.isWorking
    val image = state.image
    ComposerSection(
        header = stringResource(R.string.notification_composer_image_header),
        footer = { FooterText(stringResource(R.string.notification_composer_image_footer)) },
    ) {
        if (image != null) {
            AsyncImage(
                model = image.jpeg,
                contentDescription = stringResource(R.string.cd_notification_composer_image_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("$COMPOSER_TAG.imagePreview"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onPickImage,
                enabled = enabled,
                modifier = Modifier.testTag("$COMPOSER_TAG.imagePicker"),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (image == null) R.string.notification_composer_image_pick else R.string.notification_composer_image_change,
                    ),
                )
            }
            if (state.isPreparingImage) {
                val preparing = stringResource(R.string.cd_notification_composer_image_preparing)
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .semantics { contentDescription = preparing }
                        .testTag("$COMPOSER_TAG.imagePreparing"),
                    strokeWidth = 2.dp,
                )
            }
        }
        if (image != null) {
            TextButton(
                onClick = onRemoveImage,
                enabled = enabled,
                modifier = Modifier.testTag("$COMPOSER_TAG.imageRemove"),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notification_composer_image_remove), color = MaterialTheme.colorScheme.error)
            }
        }
        state.imageError?.let { error ->
            StatusLine(
                icon = Icons.Filled.Error,
                text = stringResource(
                    when (error) {
                        ComposerImageError.UNREADABLE -> R.string.notification_composer_image_unreadable
                        ComposerImageError.UNUSABLE -> R.string.notification_composer_image_unusable
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("$COMPOSER_TAG.imageError"),
            )
        }
    }
}
