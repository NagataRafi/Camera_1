package com.nagata.camera_1

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private val PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private val REQUEST_CODE = 100

    private var mCamera: Camera? = null
    private var mPreview: Preview? = null
    private var previewState: String = "PREVIEW"

    private val mPictureCallback = Camera.PictureCallback { data, camera ->
        val pictureFile = getOutputMediaFile()
        if (pictureFile == null) {
            Toast.makeText(this@MainActivity, "Gagal membuat file foto", Toast.LENGTH_SHORT).show()
            previewState = "PREVIEW"
            return@PictureCallback
        }
        try {
            val fos = FileOutputStream(pictureFile)
            fos.write(data)
            fos.close()
            MediaScannerConnection.scanFile(
                this@MainActivity,
                arrayOf(pictureFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            Toast.makeText(
                this@MainActivity,
                "Foto tersimpan di: ${pictureFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this@MainActivity,
                "Gagal menyimpan foto: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
        previewState = "PREVIEW"
        try {
            camera?.startPreview()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imgBtnShutter = findViewById<ImageButton>(R.id.imgBtnShutter)
        imgBtnShutter.setOnClickListener {
            if (previewState == "PREVIEW") {
                try {
                    mCamera?.takePicture(null, null, mPictureCallback)
                    previewState = "BUSY"
                } catch (e: Exception) {
                    try {
                        mCamera?.startPreview()
                    } catch (ex: Exception) {
                        // ignore
                    }
                    previewState = "PREVIEW"
                }
            } else if (previewState == "BUSY") {
                try {
                    mCamera?.startPreview()
                } catch (e: Exception) {
                    // ignore
                }
                previewState = "PREVIEW"
            }
        }
    }

    private fun cekIzinDitolak(): Boolean {
        for (permission in PERMISSIONS) {
            if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                continue
            }
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        if (cekIzinDitolak()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
            return
        }
        mCamera = getCamera()
        if (mCamera != null) {
            val frameLayoutPreview = findViewById<FrameLayout>(R.id.frameLayoutPreview)
            frameLayoutPreview.removeAllViews()
            mPreview = Preview(this, mCamera)
            frameLayoutPreview.addView(mPreview)
        }
    }

    @Suppress("DEPRECATION")
    private fun getCamera(): Camera? {
        return try {
            Camera.open()
        } catch (e: Exception) {
            Toast.makeText(this, "Kamera tidak dapat diakses", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            var allGranted = grantResults.isNotEmpty()
            for (i in permissions.indices) {
                if (permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    continue
                }
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            if (allGranted) {
                onResume()
            } else {
                Toast.makeText(this, "Izin ditolak", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            mCamera?.release()
        } catch (e: Exception) {
            // ignore
        }
        mCamera = null
        val frameLayoutPreview = findViewById<FrameLayout>(R.id.frameLayoutPreview)
        frameLayoutPreview.removeAllViews()
        mPreview = null
    }

    private fun getOutputMediaFile(): File? {
        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Camera_1"
        )
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                val fallbackDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Camera_1")
                if (!fallbackDir.exists() && !fallbackDir.mkdirs()) {
                    return null
                }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                return File(fallbackDir, "IMG_$timeStamp.jpg")
            }
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(mediaStorageDir, "IMG_$timeStamp.jpg")
    }

    @Suppress("DEPRECATION")
    inner class Preview(context: Context, private val camera: Camera?) :
        SurfaceView(context), SurfaceHolder.Callback {

        private val mHolder: SurfaceHolder = holder.apply {
            addCallback(this@Preview)
            @Suppress("DEPRECATION")
            setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            try {
                camera?.apply {
                    setPreviewDisplay(holder)
                    setDisplayOrientation(90)
                    startPreview()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memulai preview", Toast.LENGTH_SHORT).show()
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            // kosong
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            try {
                camera?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
