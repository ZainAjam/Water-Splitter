package za.westend.watersplit.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.io.File

/** What a list is looking for. */
enum class FileKind { PDF, IMAGE, MONTHS, BACKUP }

/** A PDF, photo or Water Splitter file found on the phone. [modified] is when it arrived on the phone or was last changed, whichever is later. */
data class FoundFile(val file: File, val mime: String, val modified: Long, val size: Long)

/**
 * Finds PDFs and photos in Downloads, Documents, DCIM and Pictures (and everything inside them), in WhatsApp's own folders, and
 * anything else the phone's media index knows about. Listing files saved by other apps needs Android's "all files access", which the
 * person switches on once in Settings. The newest always come first.
 */
object FileScanner {
    private val FOLDERS = listOf(
        "DCIM", "Download", "Documents", "Pictures",
        // WhatsApp keeps what it receives here on current Android versions, and in WhatsApp/Media on older ones.
        "Android/media/com.whatsapp/WhatsApp/Media",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
        "WhatsApp/Media",
    )
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
    private const val LIMIT = 3000

    fun hasAccess(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    /** Android's own switch for this app (Android 11+). */
    fun accessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun isOldBackup(f: File): Boolean = try {
        java.util.zip.ZipFile(f).use { it.getEntry("data.json") != null }
    } catch (e: Exception) {
        false
    }

    private fun mimeFor(kind: FileKind, f: File): String? {
        val ext = f.extension.lowercase()
        return when {
            kind == FileKind.PDF && ext == "pdf" -> "application/pdf"
            kind == FileKind.IMAGE && ext in IMAGE_EXTENSIONS -> "image/$ext"
            kind == FileKind.MONTHS && ext == "wsmonths" -> "application/octet-stream"
            kind == FileKind.BACKUP && ext == "wsbackup" -> "application/octet-stream"
            // backups made by older versions of the app were plain .zip files
            kind == FileKind.BACKUP && ext == "zip" && f.name.contains("backup", ignoreCase = true) && isOldBackup(f) -> "application/zip"
            else -> null
        }
    }

    /**
     * When each file arrived on the phone, from the media index (path -> milliseconds). A file copied or downloaded can keep an old
     * "modified" time from where it came from, but the index still knows when it was added. Empty for kinds the index does not cover.
     */
    private fun addedTimes(context: Context, kind: FileKind): Map<String, Long> {
        val selection = when (kind) {
            FileKind.PDF -> "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/pdf'"
            FileKind.IMAGE -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            else -> return emptyMap()
        }
        val result = HashMap<String, Long>()
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.DATE_MODIFIED),
                selection, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    result[path] = maxOf(c.getLong(1), c.getLong(2)) * 1000
                }
            }
        } catch (e: Exception) {
            // the media index is only a help; the folders are still searched
        }
        return result
    }

    private fun skip(f: File): Boolean = f.name.startsWith(".") || f.path.contains("/.")

    /** Newest first. Looks again every time it is called, so a photo taken a moment ago is found. Do not call on the main thread. */
    fun scan(kind: FileKind, context: Context? = null): List<FoundFile> {
        val root = Environment.getExternalStorageDirectory()
        val added = if (context != null) addedTimes(context, kind) else emptyMap()
        val found = HashMap<String, FoundFile>()

        fun consider(f: File) {
            if (!f.isFile || skip(f) || found.containsKey(f.path)) return
            val mime = mimeFor(kind, f) ?: return
            val size = f.length()
            if (size <= 0) return
            found[f.path] = FoundFile(f, mime, maxOf(f.lastModified(), added[f.path] ?: 0L), size)
        }

        for (name in FOLDERS) {
            File(root, name).walkTopDown()
                .onEnter { !it.name.startsWith(".") } // .thumbnails, .trashed, .Statuses and the like
                .forEach { consider(it) }
        }
        // Anything else the media index lists (another app's folder, a memory card) that the folders above did not reach.
        for (path in added.keys) {
            if (path.contains("/Android/data/") || path.contains("/Android/obb/")) continue
            consider(File(path))
        }
        // Sorted before the limit is applied, so the newest are never the ones dropped.
        return found.values.sortedByDescending { it.modified }.take(LIMIT)
    }
}
