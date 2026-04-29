package com.example.picoviewer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun queryUriDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
    }
    return null
}

/**
 * Locates the sibling `.csv` next to the picked `.txt` by choosing the CSV in the same folder whose
 * timestamp is closest to the TXT. RETINA Task does not give the TXT and CSV the same basename.
 *
 * Returns null if no same-folder CSV can be enumerated (some cloud providers only grant the picked document).
 */
fun resolveCsvSiblingUri(context: Context, txtUri: Uri, txtDisplayName: String): Uri? {
    val authority = txtUri.authority ?: return null
    val docId = try {
        DocumentsContract.getDocumentId(txtUri)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val slash = docId.lastIndexOf('/')
    if (slash < 0) return null
    val parentPrefix = docId.substring(0, slash)

    val txtTime = queryLastModified(context, txtUri)
        .takeIf { it > 0L }
        ?: parseRetinaTimestampMillis(txtDisplayName)
        ?: return null

    val childrenUri = DocumentsContract.buildChildDocumentsUri(authority, parentPrefix)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    return try {
        val candidates = mutableListOf<Pair<Uri, Long>>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getStringOrNull(nameIdx) ?: continue
                if (!name.lowercase(Locale.US).endsWith(".csv")) continue
                val childId = cursor.getStringOrNull(idIdx) ?: continue
                val csvTime = cursor.getLongOrZero(modifiedIdx)
                    .takeIf { it > 0L }
                    ?: parseRetinaTimestampMillis(name)
                    ?: continue
                candidates += DocumentsContract.buildDocumentUri(authority, childId) to kotlin.math.abs(csvTime - txtTime)
            }
        }
        candidates.minByOrNull { it.second }?.first
    } catch (_: Exception) {
        null
    }
}

private fun queryLastModified(context: Context, uri: Uri): Long {
    val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    return try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            if (cursor.moveToFirst()) cursor.getLongOrZero(idx) else 0L
        } ?: 0L
    } catch (_: Exception) {
        0L
    }
}

private fun parseRetinaTimestampMillis(fileName: String): Long? {
    val compact = Regex("""(\d{8})_(\d{6})""").find(fileName)?.destructured
    if (compact != null) {
        val (date, time) = compact
        val parsed = LocalDateTime.parse(
            "$date$time",
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US),
        )
        return parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    val readable = Regex("""(\d{4})-(\d{2})-(\d{2})\s+(\d{1,2}):(\d{2}):(\d{2})""")
        .find(fileName)
        ?.destructured
    if (readable != null) {
        val (year, month, day, hour, minute, second) = readable
        val parsed = LocalDateTime.of(
            year.toInt(),
            month.toInt(),
            day.toInt(),
            hour.toInt(),
            minute.toInt(),
            second.toInt(),
        )
        return parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    return null
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (index >= 0 && !isNull(index)) getString(index) else null

private fun android.database.Cursor.getLongOrZero(index: Int): Long =
    if (index >= 0 && !isNull(index)) getLong(index) else 0L
