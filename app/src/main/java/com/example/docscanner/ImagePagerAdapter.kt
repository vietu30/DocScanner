package com.example.docscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ImagePagerAdapter(
    private val images: List<String>
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

    class ImageViewHolder(val imageView: ImageView) :
        RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {

        val imageView = ImageView(parent.context)

        imageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Hiển thị đẹp hơn trên nhiều máy
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.adjustViewBounds = true

        return ImageViewHolder(imageView)
    }

    override fun getItemCount(): Int = images.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {

        val bitmap = decodeSampledBitmap(holder.imageView.context, images[position])
        holder.imageView.setImageBitmap(bitmap)
    }
    // ===== LOAD ẢNH NHẸ HƠN (TRÁNH LAG) =====
    private fun decodeSampledBitmap(context: Context, path: String): Bitmap? {

        return if (path.startsWith("content://")) {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(path))
            BitmapFactory.decodeStream(inputStream)
        } else {
            BitmapFactory.decodeFile(path)
        }
    }

    // ===== FIX XOAY ẢNH CHUẨN =====
   private fun rotateBitmapIfNeeded(path: String): Bitmap {

        val bitmap = BitmapFactory.decodeFile(path) ?: return Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)

        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}