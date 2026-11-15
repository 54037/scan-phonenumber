package com.example.billphone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.billphone.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /** 命中一个号码后锁定，避免重复触发 */
    @Volatile private var locked = false
    private var phoneToCall: String? = null
    private var tone: ToneGenerator? = null

    /** 只认 11 位大陆手机号 */
    private val phoneRegex = Regex("1[3-9]\\d{9}")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()
        tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

        binding.btnCall.setOnClickListener {
            phoneToCall?.let { call(it) }
        }
        binding.btnReset.setOnClickListener { resumeScan() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            // 720p 分析流：面单字体大，720p 足够，速度明显快于 1080p
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(1280, 720))
                .build()

            analysis.setAnalyzer(cameraExecutor) { proxy ->
                if (locked) {
                    proxy.close()
                    return@setAnalyzer
                }
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                recognizer.process(input)
                    .addOnSuccessListener { text ->
                        val digits = text.text.replace(Regex("\\s"), "")
                        phoneRegex.find(digits)?.value?.let { phone ->
                            if (!locked) {
                                locked = true
                                runOnUiThread { onNumberFound(phone) }
                            }
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onNumberFound(phone: String) {
        phoneToCall = phone
        binding.tvPhone.text = phone
        binding.tvStatus.text = "已识别，确认后拨打"
        binding.btnCall.isEnabled = true
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    private fun resumeScan() {
        locked = false
        phoneToCall = null
        binding.tvPhone.text = "——"
        binding.tvStatus.text = "将面单对准取景框"
        binding.btnCall.isEnabled = false
    }

    private fun call(phone: String) {
        // 跳系统拨号盘（无需额外权限）；如需直拨改用 ACTION_CALL 并申请 CALL_PHONE
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        recognizer.close()
        tone?.release()
    }
}
