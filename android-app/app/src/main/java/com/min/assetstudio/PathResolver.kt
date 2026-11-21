package com.min.assetstudio

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/** SAF URI to real path. */
object PathResolver {
    fun resolve(uri: Uri, tree: Boolean): String? = try {
        val id = if (tree) DocumentsContract.getTreeDocumentId(uri) else DocumentsContract.getDocumentId(uri)
        when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                val i = id.indexOf(':')
                if (i < 0) null else {
                    val vol = id.substring(0, i)
                    val rel = id.substring(i + 1)
                    val base = if (vol == "primary") Environment.getExternalStorageDirectory().path else "/storage/$vol"
                    if (rel.isEmpty()) base else "$base/$rel"
                }
            }
            "com.android.providers.downloads.documents" ->
                if (id.startsWith("raw:")) id.removePrefix("raw:") else null
            else -> null
        }
    } catch (e: IllegalArgumentException) {
        null
    }
}
