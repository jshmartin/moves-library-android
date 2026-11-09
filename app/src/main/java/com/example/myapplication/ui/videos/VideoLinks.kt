package com.example.myapplication.ui.videos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.VideoLinkKind
import com.example.myapplication.data.VideoLinkTarget
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.ui.library.LibraryRoutes
import com.example.myapplication.ui.practice.PracticeRoutes
import com.example.myapplication.ui.sets.SetRoutes

/** The screen a video link opens. */
internal fun VideoLinkTarget.route(): String = when (kind) {
    VideoLinkKind.MOVE -> LibraryRoutes.move(targetId)
    VideoLinkKind.SET -> SetRoutes.detail(targetId)
    VideoLinkKind.PRACTICE -> PracticeRoutes.detail(targetId)
    VideoLinkKind.SESSION -> PracticeRoutes.log(targetId)
}

/** Tappable chips for everywhere a video is linked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoLinkChips(links: List<VideoLinkTarget>, onNavigate: (String) -> Unit) {
    if (links.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEach { link ->
            AssistChip(
                onClick = { onNavigate(link.route()) },
                label = {
                    Text("${link.kind.noun}: ${link.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

/** Asks before pointing a video at a file whose content doesn't match it. */
@Composable
internal fun RelinkMismatchDialog(pending: PendingRelink, onLinkAnyway: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Different file?") },
        text = {
            Text(
                "This file's content doesn't match \"${pending.video.displayName}\". " +
                    "It may have been edited or re-encoded. Link it anyway?"
            )
        },
        confirmButton = { TextButton(onClick = onLinkAnyway) { Text("Link anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks a move, set or practice to link a video to. [current] is where it's linked already. */
@Composable
internal fun VideoLinkDialog(
    videoName: String,
    targets: List<VideoLinkTarget>,
    current: List<VideoLinkTarget>,
    onPick: (VideoLinkTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val filedUnder = current.firstOrNull { it.owner is VideoOwner.Exclusive }
    val options = targets.filterNot { it in current }.groupBy { it.kind }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link $videoName", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                if (filedUnder != null) {
                    item {
                        Text(
                            "It's on ${filedUnder.label}. Picking another move or set moves it. Practices keep it there.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (options.isEmpty()) {
                    item { Text("There's nothing else to link it to yet.") }
                }
                listOf(
                    VideoLinkKind.PRACTICE to "Practices",
                    VideoLinkKind.MOVE to "Moves",
                    VideoLinkKind.SET to "Sets",
                ).forEach { (kind, heading) ->
                    val rows = options[kind].orEmpty()
                    if (rows.isNotEmpty()) {
                        item(key = kind) {
                            Text(
                                heading,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        items(rows, key = { "${it.kind}-${it.targetId}" }) { target ->
                            Text(
                                target.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(target) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
