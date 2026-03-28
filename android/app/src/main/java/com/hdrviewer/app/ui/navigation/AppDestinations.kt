package com.hdrviewer.app.ui.navigation

import android.net.Uri as AndroidUri

object AppDestinations {
    const val PHOTOS = "photos"
    const val ALBUMS = "albums"
    const val SETTINGS = "settings"
    const val ALBUM_DETAIL = "album_detail"
    const val VIEWER = "viewer"

    fun albumDetailRoute(bucketId: String): String =
        "$ALBUM_DETAIL/${AndroidUri.encode(bucketId, "")}"

    fun viewerRoute(startKey: String): String =
        "$VIEWER/${AndroidUri.encode(startKey, "")}"
}
