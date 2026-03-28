package com.example.docscanner.model

data class ImageResponse(
    val status: Boolean,
    val message: String,
    val data: List<ImageItem>
)

data class ImageItem(
    val id: Int,
    val description: String?,
    val imageUrl: String,
    val uploadDate: Long
)
