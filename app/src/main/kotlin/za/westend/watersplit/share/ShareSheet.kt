package za.westend.watersplit.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareSheet {
    /** Opens Android's share menu for any file (for example a .wsmonths file) kept in the cache's `exports` folder. */
    fun shareFile(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Opens Android's normal share menu (every app that accepts images) for [image]. */
    fun shareImage(context: Context, image: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(image.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

/** Shares an invoice (PDF) or meter photo kept in the app's private storage: a copy goes to the cache's `exports` folder first. */
fun ShareSheet.shareAttachment(context: Context, file: File, mime: String, title: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val copy = File(dir, "${title.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().ifEmpty { "file" }}.${file.extension.ifEmpty { if (mime == "application/pdf") "pdf" else "jpg" }}")
    file.copyTo(copy, overwrite = true)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        clipData = ClipData.newRawUri(copy.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, title)
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** Saves a copy of [file] in the phone's Downloads folder as [name] (Android adds " (1)" if that name is taken). Returns false if it could not be saved. */
fun ShareSheet.saveToDownloads(context: Context, file: File, mime: String, name: String): Boolean = try {
    val clean = name.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().ifEmpty { "file" } +
        "." + file.extension.ifEmpty { if (mime == "application/pdf") "pdf" else "jpg" }
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, clean)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            false
        } else {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            true
        }
    } else {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        file.copyTo(File(dir, clean), overwrite = false)
        true
    }
} catch (e: Exception) {
    false
}
