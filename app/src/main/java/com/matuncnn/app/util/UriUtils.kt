package com.matuncnn.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object UriUtils {
    fun getFileName(uri: Uri, context: Context): String? {
        if (uri.scheme == "file") return uri.path?.let(::File)?.name
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }
}
