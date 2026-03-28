package com.example.docscanner

import java.io.File

data class ScanItem(
    val fileName: String,
    val fileInfo: String,
    val file: File? = null,
    val imageUrl: String? = null,
    val serverId: Int? = null
)