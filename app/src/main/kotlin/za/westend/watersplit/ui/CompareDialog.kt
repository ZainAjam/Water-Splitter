package za.westend.watersplit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/**
 * The document (invoice or meter photo) and the numbers read from it on one screen, so each number can be checked and corrected
 * without going back and forth. Upright phone: document on top, numbers underneath. Sideways phone: side by side.
 */
@Composable
fun CompareDialog(title: String, file: File, mime: String, onClose: () -> Unit, numbers: @Composable ColumnScope.() -> Unit) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BackArrow(onClose)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
                if (landscape) {
                    Row(Modifier.fillMaxSize()) {
                        DocumentPane(file, mime, Modifier.weight(1f).fillMaxSize())
                        Column(
                            Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = numbers,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        DocumentPane(file, mime, Modifier.weight(1f).fillMaxWidth())
                        HorizontalDivider(thickness = 2.dp)
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = numbers,
                        )
                    }
                }
            }
        }
    }
}

/** The document, scrollable in both directions, with zoom buttons so the small print can be read. */
@Composable
private fun DocumentPane(file: File, mime: String, modifier: Modifier) {
    val pages by rememberPages(file, mime, 1600, 6)
    var zoom by remember { mutableFloatStateOf(1f) }
    val config = LocalConfiguration.current
    // In landscape the document only gets half the width.
    val paneWidth = (if (config.screenWidthDp > config.screenHeightDp) config.screenWidthDp / 2 else config.screenWidthDp).dp - 16.dp
    Box(modifier) {
        val list = pages
        when {
            list == null -> Text("Loading...", Modifier.padding(16.dp))
            list.isEmpty() -> Text("This file can't be shown here.", Modifier.padding(16.dp))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.width(paneWidth * zoom),
                    )
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }) { Text("−") }
            OutlinedButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(4f) }) { Text("+") }
        }
    }
}
