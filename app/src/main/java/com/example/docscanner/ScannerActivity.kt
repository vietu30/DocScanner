package com.example.docscanner

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.permissionx.guolindev.PermissionX
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.previewView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        requestCameraPermission()
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

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {

                it.setSurfaceProvider(previewView.surfaceProvider)

            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview
            )

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}