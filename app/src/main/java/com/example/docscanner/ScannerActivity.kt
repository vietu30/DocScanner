package com.example.docscanner

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.docscanner.databinding.ActivityScannerBinding
import com.permissionx.guolindev.PermissionX
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.activity.result.contract.ActivityResultContracts

class ScannerActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityScannerBinding

    // ✅ LIST ẢNH (QUAN TRỌNG)
    private val imageList = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        previewView = binding.previewView
        cameraExecutor = Executors.newSingleThreadExecutor()

        requestCameraPermission()

        // ===== CHỤP =====
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        // ===== CLICK PREVIEW =====
        binding.imgPreview.setOnClickListener {
            if (imageList.isNotEmpty()) {
                openPreview()
            }
        }

        // ===== DONE =====
        binding.btnDone.setOnClickListener {

            if (imageList.isEmpty()) {
                Toast.makeText(this, "Chưa có ảnh", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            openPreview()
        }
    }

    // ===== MỞ PREVIEW =====
    private fun openPreview() {
        val intent = Intent(this, PreviewActivity::class.java)
        intent.putStringArrayListExtra("image_list", ArrayList(imageList))
        startActivity(intent)
    }

    // ===== XIN QUYỀN =====
    private fun requestCameraPermission() {
        PermissionX.init(this)
            .permissions(Manifest.permission.CAMERA)
            .request { allGranted, _, deniedList ->
                if (allGranted) {
                    startCamera()
                } else {
                    Toast.makeText(this, "Denied: $deniedList", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // ===== CHỤP ẢNH =====
    private fun takePhoto() {

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "IMG_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    // ❗ CHỈ CROP - KHÔNG ADD LIST Ở ĐÂY
                    openCrop(photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@ScannerActivity,
                        "Error: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // ===== CROP =====
    private fun openCrop(imagePath: String) {

        val sourceUri = Uri.fromFile(File(imagePath))
        val destinationUri = Uri.fromFile(
            File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        )

        cropLauncher.launch(
            UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1.414f)
                .withMaxResultSize(1080, 1920)
                .getIntent(this)
        )
    }

    // ===== KẾT QUẢ CROP =====
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        if (result.resultCode == RESULT_OK) {

            val data = result.data ?: return@registerForActivityResult
            val resultUri = UCrop.getOutput(data) ?: return@registerForActivityResult

            val croppedPath = resultUri.path ?: return@registerForActivityResult
            // ✅ ADD VÀO LIST Ở ĐÂY (QUAN TRỌNG NHẤT)
            imageList.add(croppedPath)
            Log.d("SIZE", "Scanner = ${imageList.size}")

            // ✅ update preview
            val bitmap = rotateBitmapIfNeeded(croppedPath)
            binding.imgPreview.setImageBitmap(bitmap)
            binding.imgPreview.visibility = View.VISIBLE
        }
    }

    // ===== CAMERA =====
    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )

        }, ContextCompat.getMainExecutor(this))
    }

    // ===== XOAY ẢNH =====
    private fun rotateBitmapIfNeeded(path: String): Bitmap {

        val bitmap = BitmapFactory.decodeFile(path)

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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}