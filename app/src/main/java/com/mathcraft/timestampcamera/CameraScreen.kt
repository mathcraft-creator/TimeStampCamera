package com.mathcraft.timestampcamera

import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun CameraScreen(
    config: StampConfig,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val locationHelper = remember { LocationHelper(context) }

    var saving by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf("") }
    var cachedAddress by remember { mutableStateOf<String?>(null) }

    // 위치 업데이트 시작/종료
    DisposableEffect(Unit) {
        locationHelper.start()
        onDispose { locationHelper.stop() }
    }

    // 카메라 바인딩
    LaunchedEffect(previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                // 바인딩 실패 무시 (로그만)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // 미리보기 각인 문구를 1초마다 갱신
    LaunchedEffect(config, cachedAddress) {
        while (true) {
            val loc = if (config.showGps || config.showAddress) locationHelper.current() else null
            val lines = ImageStamper.buildLines(config, Date(), loc, cachedAddress)
            previewText = lines.joinToString("\n")
            delay(1000)
        }
    }

    // 주소 각인이 켜져 있으면 5초마다 주소 갱신 (역지오코딩은 무거우므로 별도로)
    LaunchedEffect(config.showAddress) {
        if (!config.showAddress) {
            cachedAddress = null
            return@LaunchedEffect
        }
        while (true) {
            val loc = locationHelper.current()
            if (loc != null) {
                val addr = withContext(Dispatchers.IO) { locationHelper.addressOf(loc) }
                if (addr != null) cachedAddress = addr
            }
            delay(5000)
        }
    }

    fun takePhoto() {
        if (saving) return
        saving = true
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        val uri = withContext(Dispatchers.Default) {
                            try {
                                val bmp = imageProxyToBitmap(image)
                                val loc = if (config.showGps || config.showAddress) {
                                    locationHelper.current()
                                } else null
                                val address = if (config.showAddress && loc != null) {
                                    locationHelper.addressOf(loc)
                                } else null
                                val lines = ImageStamper.buildLines(config, Date(), loc, address)
                                val stamped = ImageStamper.stamp(bmp, config, lines)
                                saveBitmapToGallery(context, stamped)
                            } catch (e: Exception) {
                                null
                            } finally {
                                image.close()
                            }
                        }
                        saving = false
                        Toast.makeText(
                            context,
                            if (uri != null) "저장 완료: 갤러리 > TimestampCamera" else "저장 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    saving = false
                    Toast.makeText(context, "촬영 오류: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 실시간 각인 미리보기
        if (previewText.isNotBlank()) {
            val overlayAlignment = when (config.position) {
                StampPosition.TOP_LEFT -> Alignment.TopStart
                StampPosition.TOP_RIGHT -> Alignment.TopEnd
                StampPosition.BOTTOM_LEFT -> Alignment.BottomStart
                StampPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                StampPosition.BOTTOM_CENTER -> Alignment.BottomCenter
            }
            val textAlign = when (config.position) {
                StampPosition.TOP_LEFT, StampPosition.BOTTOM_LEFT -> TextAlign.Start
                StampPosition.TOP_RIGHT, StampPosition.BOTTOM_RIGHT -> TextAlign.End
                StampPosition.BOTTOM_CENTER -> TextAlign.Center
            }
            Text(
                text = previewText,
                color = Color(config.color.color),
                fontSize = 16.sp,
                textAlign = textAlign,
                modifier = Modifier
                    .align(overlayAlignment)
                    .padding(20.dp)
                    .padding(bottom = if (overlayAlignment == Alignment.BottomCenter) 96.dp else 0.dp)
            )
        }

        // 설정 버튼
        FloatingActionButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "설정")
        }

        // 촬영 버튼
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(78.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (saving) {
                CircularProgressIndicator(color = Color.Black)
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0D47A1))
                        .clickable { takePhoto() }
                )
            }
        }
    }
}
