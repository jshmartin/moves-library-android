package com.example.myapplication.ui.library

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myapplication.data.model.MoveStatus
import java.util.Locale

const val MAX_SCORE = 5

val MoveStatus.label: String
    get() = when (this) {
        MoveStatus.LEARNING -> "Learning"
        MoveStatus.IN_ARSENAL -> "In arsenal"
        MoveStatus.BATTLE_READY -> "Battle-ready"
    }

fun formatAverage(average: Double): String = String.format(Locale.getDefault(), "%.1f", average)

/** Five tappable stars; [selected] of 0 means nothing is chosen yet. */
@Composable
fun RatingStars(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        for (score in 1..MAX_SCORE) {
            IconButton(onClick = { onSelect(score) }) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Rate $score of $MAX_SCORE",
                    tint = if (score <= selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }
        }
    }
}
