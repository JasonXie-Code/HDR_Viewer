package com.hdrviewer.app.ui

import com.hdrviewer.app.data.GalleryMedia

/** 相册网格行：日期标题（按时间排序时）或缩略图单元。 */
sealed class GalleryGridCell {
    data class DayHeader(val label: String) : GalleryGridCell()
    data class Thumb(val media: GalleryMedia) : GalleryGridCell()
}
