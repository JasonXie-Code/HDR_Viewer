package com.hdrviewer.app

import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import com.hdrviewer.app.data.GalleryMedia

object MediaShare {
    fun share(context: Context, items: List<GalleryMedia>) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            val m = items.first()
            ShareCompat.IntentBuilder(context)
                .setType(m.mimeType ?: "*/*")
                .setStream(m.uri)
                .intent
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .let { context.startActivity(Intent.createChooser(it, null)) }
        } else {
            val distinctMime = items.mapNotNull { it.mimeType }.distinct()
            val mime = if (distinctMime.size == 1) distinctMime.first() else "*/*"
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }
}
