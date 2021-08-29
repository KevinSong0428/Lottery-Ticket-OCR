package com.example.textrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import android.util.Log
import android.view.View
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.ImageAnalysisConfig
import java.lang.Exception
import android.os.Vibrator
import android.graphics.Matrix
import android.widget.ImageView





class MainActivity : AppCompatActivity() {

companion object {
    private const val TAG = "CameraXBasic"
    private const val REQUEST_CODE_PERMISSIONS = 1
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE)
}

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var bitmapPhoto: Bitmap? = null

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Set up the listener for take photo button
        capture_image.setOnClickListener {
            vibrator.vibrate(200)
            takePhoto()
        }

        release_image.setOnClickListener {
            vibrator.vibrate(200)
            releaseImage()
        }
    }

    //CAMERA - BEGIN
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = buildPreview()

            imageCapture = buildImageCapture()

            //imageAnalysis
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Select back camera as a default
            //val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun buildPreview(): Preview = Preview.Builder()
        .build()
        .also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

    private fun buildImageCapture(): ImageCapture = ImageCapture.Builder()
        .setTargetRotation(previewView.display.rotation)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),object : ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UnsafeOptInUsageError")
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                super.onCaptureSuccess(imageProxy)
                Toast.makeText(applicationContext, "Clicked", Toast.LENGTH_LONG).show()
                val photo = imageProxy.image
                val rotation = imageProxy.imageInfo.rotationDegrees

                //convert imageProxy to bitmap to display
                bitmapPhoto = imageProxyToBitmap(imageProxy)
                imageView.setImageBitmap(bitmapPhoto)

                //make preview invisible to display captured image
                previewView.visibility = View.INVISIBLE
                capture_image.visibility = View.INVISIBLE
                imageView.visibility = View.VISIBLE
                release_image.visibility = View.VISIBLE

                val imageInput = InputImage.fromMediaImage(photo!!, rotation)

                // Make sure to close the image
                imageProxy.close()

                recognizeText(imageInput)
            }
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(applicationContext, "Failed", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }
        })
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer[bytes]
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun recognizeText(image: InputImage)
    {
        //Instance of TextRecognizer
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val result = recognizer.process(image)
            .addOnSuccessListener { visionText ->
                var i = 0
                var found : Boolean = false
                var dateText = ""
                val keywords = arrayOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
                var day = ""
                /*for (block in visionText.textBlocks)
                {
                    textView.append(block.lines[0].text + "\n")
                }*/

                for (block in visionText.textBlocks)
                {
                    var lineText = block.lines[0].text
                    lineText = lineText.filter { it.isDigit() }
                    if (lineText != "" && lineText.length == 12 && !found)
                    {
                        textView.append("Number Set: \n")
                        for (i in lineText.indices)
                        {
                            if (i <10)
                            {
                                if (i % 2 == 0) textView.append(" ")
                                textView.append(lineText[i].toString())
                            }
                            if (i % 2 == 0 && i >= 10)
                            {
                                textView.append("\n Special Number: \n" + lineText.substring(i, i + 2) + "\n")
                                continue
                            }
                        }
                        found = true
                    }

                    if (found)
                    {
                        dateText = block.lines[0].text
                        if (dateText.length > 3) {
                            day = dateText.substring(0, 3)
                        }
                        if (day in keywords)
                        {
                            //textView.append("DAY: \n$day\n")
                            textView.append("Drawing Date: \n$dateText\n")
                            break
                        }
                    }
                    //textView.append(i.toString() + " - " + block.lines[0].text + "\n")
                    i += 1
                }
                //textView.setText(visionText.text)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: " + e.message, Toast.LENGTH_LONG).show()
            }
    }

    //PERMISSIONS - BEGIN
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permission for camera was denied." +
                        "Please change in the settings.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    //PERMISSIONS - END

    //change screens back to preview and clear textView and imageView
    private fun releaseImage() {
        //reset
        textView.setText("")
        previewView.visibility = View.VISIBLE
        capture_image.visibility = View.VISIBLE
        //hide
        imageView.visibility = View.INVISIBLE
        release_image.visibility = View.INVISIBLE

        bitmapPhoto?.recycle()
        bitmapPhoto = null
    }

}
