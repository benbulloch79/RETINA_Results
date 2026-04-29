package com.example.picoviewer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

fun queryUriDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
    }
    return null
}

/**
 * Locates a sibling `.csv` next to the picked `.txt` by swapping the filename extension for the
 * same parent folder (External Storage Documents provider ids).
 *
 * Returns null if the CSV does not exist or the URI scheme cannot produce a sibling id (e.g. some cloud providers).
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
    val csvLeaf = txtDisplayName.trim().replace(Regex("(?i)\\.txt$"), ".csv")
    val csvDocId = "$parentPrefix/$csvLeaf"
    val csvUri = DocumentsContract.buildDocumentUri(authority, csvDocId)
    return try {
        val stream = context.contentResolver.openInputStream(csvUri) ?: return null
        stream.use { }
        csvUri
    } catch (_: Exception) {
        null
    }
}
