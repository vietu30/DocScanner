package com.example.docscanner.network

data class UploadResponse(
    val status: Boolean,
    val message: String,
    val data: UploadData?
)

data class UploadData(
    val imageUrl: String,
    val description: String
)
