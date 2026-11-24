package za.westend.watersplit.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Previews {
    /** A photo, scaled down to at most [maxWidth] wide, with the camera's rotation applied. */
    fun image(file: File, maxWidth: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                if (w > maxWidth) decoder.setTargetSize(maxWidth, (h.toLong() * maxWidth / w).toInt())
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Exception) {
        null
    }

    /** The first [maxPages] pages of a PDF drawn [width] pixels wide. */
    fun pdfPages(file: File, width: Int, maxPages: Int): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                val page = renderer.openPage(i)
                try {
                    val h = (page.height.toLong() * width / page.width).toInt()
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bmp)
                } finally {
                    page.close()
                }
            }
        } catch (e: Exception) {
            // return whatever pages we managed
        } finally {
            renderer?.close()
            pfd?.close()
        }
        return pages
    }

    /** A photo shrunk to at most [maxWidth] wide and re-saved as JPEG (what gets sent for automatic reading). */
    fun jpegBytes(file: File, maxWidth: Int, quality: Int = 90): ByteArray? {
        val bmp = image(file, maxWidth) ?: return null
        return try {
            java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        } finally {
            bmp.recycle()
        }
    }

    fun load(file: File, mime: String, width: Int, maxPages: Int): List<Bitmap> =
        if (mime == "application/pdf") pdfPages(file, width, maxPages) else listOfNotNull(image(file, width))
}

@Composable
internal fun rememberPages(file: File, mime: String, width: Int, maxPages: Int): State<List<Bitmap>?> =
    produceState<List<Bitmap>?>(null, file, mime, width, maxPages) {
        value = withContext(Dispatchers.IO) { Previews.load(file, mime, width, maxPages) }
    }

/** A small picture of the first page / the photo. */
@Composable
fun AttachmentThumb(file: File, mime: String, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val pages by rememberPages(file, mime, 600, 1)
    val first = pages?.firstOrNull()
    if (first != null) {
        Image(
            bitmap = first.asImageBitmap(),
            contentDescription = "Preview",
            contentScale = ContentScale.Fit,
            modifier = modifier.height(height),
        )
    } else {
        Text(if (pages == null) "Loading preview..." else "No preview available", style = MaterialTheme.typography.bodySmall)
    }
}

/** Full-screen look at an invoice or meter photo, with zoom buttons so the small print can be read. */
@Composable
fun AttachmentViewer(title: String, file: File, mime: String, onClose: () -> Unit, onShare: (() -> Unit)? = null, onDownload: (() -> Unit)? = null) {
    val pages by rememberPages(file, mime, 1600, 6)
    var zoom by remember { mutableFloatStateOf(1f) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp - 24.dp

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackArrow(onClose)
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }) { Text("−") }
            OutlinedButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(3f) }) { Text("+") }
        }
        val list = pages
        when {
            list == null -> Text("Loading...", Modifier.padding(16.dp))
            list.isEmpty() -> Text("This file can't be shown here.", Modifier.padding(16.dp))
            else -> Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                list.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.width(screenWidth * zoom),
                    )
                }
            }
        }
    }
    // Floating Share button: opens Android's share menu for this PDF or photo.
    if (onShare != null || onDownload != null) {
        Row(Modifier.align(Alignment.BottomEnd).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onDownload != null) {
                FloatingActionButton(onClick = onDownload) { Icon(DownloadIcon, contentDescription = "Download to this phone") }
            }
            if (onShare != null) {
                FloatingActionButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share") }
            }
        }
    }
    }
}

/** Material "download" arrow, drawn here so the app does not need the large extended icon set. */
private val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder("Download", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) {
            moveTo(5f, 20f); horizontalLineTo(19f); verticalLineTo(18f); horizontalLineTo(5f); close()
            moveTo(19f, 9f); horizontalLineTo(15f); verticalLineTo(3f); horizontalLineTo(9f); verticalLineTo(9f); horizontalLineTo(5f)
            lineTo(12f, 16f); close()
        }
    }.build()
}
