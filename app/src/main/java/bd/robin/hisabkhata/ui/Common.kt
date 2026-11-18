@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package bd.robin.hisabkhata.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.AppContainer
import bd.robin.hisabkhata.logic.SlipDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }

object PendingCapture {
    var slip: SlipDraft? = null
    var suggestedName: String? = null
    var photoPath: String? = null
    var faceEmbedding: FloatArray? = null
    var faceModel: String? = null
    var barcode: String? = null

    fun clearFace() {
        photoPath = null
        faceEmbedding = null
        faceModel = null
        suggestedName = null
    }
}

fun Modifier.tag(id: String): Modifier = semantics {
    testTagsAsResourceId = true
    testTag = id
}

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigOutlined(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Keypad(onDigit: (Int) -> Unit, onDelete: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(
        listOf("1" to "key_1", "2" to "key_2", "3" to "key_3"),
        listOf("4" to "key_4", "5" to "key_5", "6" to "key_6"),
        listOf("7" to "key_7", "8" to "key_8", "9" to "key_9"),
        listOf("C" to "key_clear", "0" to "key_0", "⌫" to "key_del"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, id) ->
                    OutlinedButton(
                        onClick = {
                            when (id) {
                                "key_clear" -> onClear()
                                "key_del" -> onDelete()
                                else -> onDigit(label.toInt())
                            }
                        },
                        modifier = Modifier.weight(1f).height(58.dp).tag(id),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PartyAvatar(name: String, path: String?, size: Dp) {
    val letter = name.trim().firstOrNull()?.toString() ?: "?"
    val palette = listOf(
        Color(0xFF0E7A45), Color(0xFF1F7A8C), Color(0xFFC2412D),
        Color(0xFFE39B12), Color(0xFF5C4B8A), Color(0xFF2E6B4E),
    )
    val tint = palette[kotlin.math.abs(name.hashCode()) % palette.size]
    val bitmap = rememberBitmap(path)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun rememberBitmap(path: String?): android.graphics.Bitmap? {
    val bitmap = produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) null else BitmapFactory.decodeFile(path)
        }
    }
    return bitmap.value
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ToneCard(title: String, value: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = container, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = content, style = MaterialTheme.typography.labelLarge)
            Text(value, color = content, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun RowScope.ActionTile(
    title: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 84.dp).tag(tag),
        shape = RoundedCornerShape(18.dp),
        color = tint.copy(alpha = 0.14f),
    ) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.CenterStart) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
        }
    }
}

fun android.content.Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

@Composable
fun GroupedBars(
    groups: List<Pair<String, Pair<Float, Float>>>,
    colorA: Color,
    colorB: Color,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty() || groups.all { it.second.first == 0f && it.second.second == 0f }) return
    androidx.compose.foundation.Canvas(modifier.height(160.dp).fillMaxWidth()) {
        val maxV = groups.maxOf { maxOf(it.second.first, it.second.second) }.coerceAtLeast(1f)
        val gap = 6.dp.toPx()
        val groupW = (size.width - gap * (groups.size + 1)) / groups.size
        groups.forEachIndexed { index, group ->
            val left = gap + index * (groupW + gap)
            val barW = groupW * 0.38f
            val h1 = (group.second.first / maxV) * (size.height - 4.dp.toPx())
            val h2 = (group.second.second / maxV) * (size.height - 4.dp.toPx())
            drawRoundRect(colorA, androidx.compose.ui.geometry.Offset(left, size.height - h1), androidx.compose.ui.geometry.Size(barW, h1.coerceAtLeast(2f)), androidx.compose.ui.geometry.CornerRadius(8f, 8f))
            drawRoundRect(colorB, androidx.compose.ui.geometry.Offset(left + barW + 4f, size.height - h2), androidx.compose.ui.geometry.Size(barW, h2.coerceAtLeast(2f)), androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        groups.forEach { Text(it.first, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1) }
    }
}

@Composable
fun rememberContainer() = LocalContainer.current
