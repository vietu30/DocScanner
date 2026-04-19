package com.example.docscanner

import android.Manifest
import android.content.Context
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

    private val imageList = ArrayList<String>()

    // Flash state
    private var isFlashEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        previewView = binding.previewView
        cameraExecutor = Executors.newSingleThreadExecutor()

        // ── Đọc Settings từ SharedPreferences ────────────────────────────
        val prefs = getSharedPreferences("camera_settings", Context.MODE_PRIVATE)

        // Grid lines
        val showGrid = prefs.getBoolean("grid_lines", true)
        binding.gridOverlay.visibility = if (showGrid) View.VISIBLE else View.GONE

        // Flash (đọc trạng thái mặc định từ settings)
        isFlashEnabled = prefs.getBoolean("auto_flash", false)
        updateFlashIcon()

        // ── Nút Back → về MainActivity ───────────────────────────────────
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // ── Nút Flash toggle ─────────────────────────────────────────────
        binding.btnFlash.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            updateFlashIcon()
            // Áp dụng ngay vào imageCapture nếu đã khởi tạo
            if (::imageCapture.isInitialized) {
                imageCapture.flashMode = if (isFlashEnabled)
                    ImageCapture.FLASH_MODE_ON
                else
                    ImageCapture.FLASH_MODE_OFF
            }
        }

        // ── Nút Chụp ─────────────────────────────────────────────────────
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        // ── Click Preview thumbnail ───────────────────────────────────────
        binding.imgPreview.setOnClickListener {
            if (imageList.isNotEmpty()) openPreview()
        }

        // ── Nút Done ─────────────────────────────────────────────────────
        binding.btnDone.setOnClickListener {
            if (imageList.isEmpty()) {
                Toast.makeText(this, "Chưa có ảnh nào", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openPreview()
        }

        requestCameraPermission()
    }

    // ── Cập nhật icon Flash ───────────────────────────────────────────────
    private fun updateFlashIcon() {
        binding.btnFlash.setImageResource(
            if (isFlashEnabled) R.drawable.icon_flash_on else R.drawable.icon_flash_off
        )
    }

    // ── Mở PreviewActivity ────────────────────────────────────────────────
    private fun openPreview() {
        val intent = Intent(this, PreviewActivity::class.java)
        intent.putStringArrayListExtra("image_list", ArrayList(imageList))
        startActivity(intent)
    }

    // ── Xin quyền Camera ─────────────────────────────────────────────────
    private fun requestCameraPermission() {
        PermissionX.init(this)
            .permissions(Manifest.permission.CAMERA)
            .request { allGranted, _, deniedList ->
                if (allGranted) startCamera()
                else Toast.makeText(this, "Cần quyền Camera: $deniedList", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Chụp ảnh ─────────────────────────────────────────────────────────
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
                    openCrop(photoFile.absolutePath)
                }
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@ScannerActivity, "Lỗi: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ── Mở UCrop ─────────────────────────────────────────────────────────
    private fun openCrop(imagePath: String) {
        val sourceUri = Uri.fromFile(File(imagePath))
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))

        cropLauncher.launch(
            UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1.414f)
                .withMaxResultSize(1080, 1920)
                .getIntent(this)
        )
    }

    // ── Kết quả Crop ─────────────────────────────────────────────────────
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val resultUri = UCrop.getOutput(data) ?: return@registerForActivityResult
            val croppedPath = resultUri.path ?: return@registerForActivityResult

            imageList.add(croppedPath)
            Log.d("Scanner", "Ảnh đã chụp: ${imageList.size}")

            val bitmap = rotateBitmapIfNeeded(croppedPath)
            binding.imgPreview.setImageBitmap(bitmap)
            binding.imgPreview.visibility = View.VISIBLE
        }
    }

    // ── Khởi động Camera ─────────────────────────────────────────────────
    private fun startCamera() {
        val prefs = getSharedPreferences("camera_settings", Context.MODE_PRIVATE)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(
                    if (prefs.getBoolean("auto_flash", false))
                        ImageCapture.FLASH_MODE_ON
                    else
                        ImageCapture.FLASH_MODE_OFF
                )
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

        }, ContextCompat.getMainExecutor(this))
    }

    // ── Xoay ảnh theo EXIF ───────────────────────────────────────────────
    private fun rotateBitmapIfNeeded(path: String): Bitmap {
        val bitmap = BitmapFactory.decodeFile(path)
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}