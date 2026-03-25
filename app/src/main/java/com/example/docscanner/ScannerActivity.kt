package com.example.docscanner

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.docscanner.databinding.ActivityScannerBinding
import com.permissionx.guolindev.PermissionX
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.io.File
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface

class ScannerActivity : AppCompatActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var binding: ActivityScannerBinding
    private var lastImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        previewView = binding.previewView

        cameraExecutor = Executors.newSingleThreadExecutor()

        requestCameraPermission()

        binding.imgPreview.setOnClickListener {

            if (lastImagePath != null) {
                val intent = Intent(this, PreviewActivity::class.java)
                intent.putExtra("image_path", lastImagePath)
                startActivity(intent)
            }
        }
    }

    private fun requestCameraPermission() {

        PermissionX.init(this)
            .permissions(Manifest.permission.CAMERA)
            .request { allGranted, _, deniedList ->

                if (allGranted) {

                    Toast.makeText(
                        this,
                        "Camera permissions are granted",
                        Toast.LENGTH_LONG
                    ).show()

                    startCamera()

                } else {

                    Toast.makeText(
                        this,
                        "These permissions are denied: $deniedList",
                        Toast.LENGTH_LONG
                    ).show()

                }

            }
    }
    private fun takePhoto(){
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
                    lastImagePath = photoFile.absolutePath
                    Toast.makeText(
                        this@ScannerActivity,
                        "Saved: ${photoFile.absolutePath}",
                        Toast.LENGTH_SHORT
                    ).show()

                    val bitmap = rotateBitmapIfNeeded(photoFile.absolutePath)
                    binding.imgPreview.setImageBitmap(bitmap)
                    binding.imgPreview.visibility = View.VISIBLE
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