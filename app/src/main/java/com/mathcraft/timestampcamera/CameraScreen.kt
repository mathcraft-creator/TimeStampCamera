package com.mathcraft.timestampcamera

import android.content.res.Configuration
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun CameraScreen(
    config: StampConfig,
    onOpenSettings: () -> Unit,
    onChange: (StampConfig) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val displayRotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val displayAspectRatio = frameAspectRatio(config.aspectRatio, isLandscape)

    val previewView = remember {
        PreviewView(context).also(PreviewFilterApplier::prepare)
    }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    val locationHelper = remember { LocationHelper(context) }
    val bindRequests = remember { LatestRequestGuard() }

    var saving by remember { mutableStateOf(false) }
    var filterPanelExpanded by rememberSaveable { mutableStateOf(false) }
    var previewText by remember { mutableStateOf("") }
    var cachedAddress by remember { mutableStateOf<String?>(null) }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    var requestedZoomRatio by rememberSaveable { mutableStateOf(1f) }

    fun requestZoom(camera: Camera, target: Float) {
        requestedZoomRatio = target
        val request = camera.cameraControl.setZoomRatio(target)
        request.addListener({
            try {
                request.get()
            } catch (_: Exception) {
                if (camera !== boundCamera) return@addListener
                val actual = camera.cameraInfo.zoomState.value?.zoomRatio ?: zoomRatio
                requestedZoomRatio = zoomRatioAfterFailure(
                    failedTarget = target,
                    latestRequested = requestedZoomRatio,
                    actualZoomRatio = actual
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // 위치 업데이트 시작/종료
    DisposableEffect(Unit) {
        locationHelper.start()
        onDispose {
            bindRequests.invalidate()
            cameraProvider?.unbindAll()
            boundCamera = null
            locationHelper.stop()
        }
    }

    // 카메라 바인딩
    LaunchedEffect(previewView, lensFacing, config.aspectRatio, displayRotation) {
        val requestToken = bindRequests.start()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!bindRequests.isCurrent(requestToken)) return@addListener
            val provider = future.get()
            cameraProvider = provider
            
            val cameraAspectRatio = when (config.aspectRatio) {
                StampAspectRatio.RATIO_9_16 -> AspectRatio.RATIO_16_9
                else -> AspectRatio.RATIO_4_3 // 1:1 도 4:3 소스에서 자르는 것이 품질상 유리함
            }

            val preview = Preview.Builder()
                .setTargetAspectRatio(cameraAspectRatio)
                .setTargetRotation(displayRotation)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(cameraAspectRatio)
                .setTargetRotation(displayRotation)
                .build()
            
            imageCapture.value = capture

            val selectedCamera = when (lensFacing) {
                CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }
            try {
                if (!provider.hasCamera(selectedCamera)) {
                    Toast.makeText(context, "해당 카메라를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addListener
                }
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selectedCamera,
                    preview,
                    capture
                )
                boundCamera = camera
                val zoomState = camera.cameraInfo.zoomState.value
                if (zoomState != null) {
                    minZoomRatio = zoomState.minZoomRatio
                    maxZoomRatio = zoomState.maxZoomRatio
                    zoomRatio = zoomState.zoomRatio
                    requestedZoomRatio = requestedZoomRatio.coerceIn(minZoomRatio, maxZoomRatio)
                    requestZoom(camera, requestedZoomRatio)
                }
            } catch (e: Exception) {
                // 바인딩 실패 무시 (로그만)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(previewView, config.photoFilter, config.photoFilterIntensity) {
        PreviewFilterApplier.update(
            previewView = previewView,
            preset = config.photoFilter,
            intensity = config.photoFilterIntensity
        )
    }

    DisposableEffect(previewView) {
        onDispose { PreviewFilterApplier.clear(previewView) }
    }

    DisposableEffect(boundCamera, lifecycleOwner) {
        val zoomState = boundCamera?.cameraInfo?.zoomState
        val observer = Observer<androidx.camera.core.ZoomState> { state ->
            minZoomRatio = state.minZoomRatio
            maxZoomRatio = state.maxZoomRatio
            zoomRatio = state.zoomRatio
        }
        zoomState?.observe(lifecycleOwner, observer)
        onDispose { zoomState?.removeObserver(observer) }
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
        val capture = imageCapture.value ?: return
        if (saving) return
        val captureConfig = config
        saving = true
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        val uri = withContext(Dispatchers.Default) {
                            try {
                                var bmp = imageProxyToBitmap(image)

                                // 1:1 비율이면 크롭
                                if (captureConfig.aspectRatio == StampAspectRatio.RATIO_1_1) {
                                    bmp = cropToSquare(bmp)
                                }

                                // 뷰티 필터(잡티 보정/화사함) 적용 — 각인/테두리보다 먼저 적용해
                                // 텍스트나 로고는 필터 영향을 받지 않게 한다.
                                if (captureConfig.beautyEnabled) {
                                    val filtered = BeautyFilter.apply(
                                        bmp,
                                        captureConfig.beautySmooth,
                                        captureConfig.beautyBrighten
                                    )
                                    if (filtered !== bmp) bmp.recycle()
                                    bmp = filtered
                                }

                                val colorFiltered = PhotoFilter.applyOrOriginal(
                                    bmp,
                                    captureConfig.photoFilter,
                                    captureConfig.photoFilterIntensity
                                )
                                if (colorFiltered !== bmp) bmp.recycle()
                                bmp = colorFiltered

                                val loc = if (captureConfig.showGps || captureConfig.showAddress) {
                                    locationHelper.current()
                                } else null
                                val address = if (captureConfig.showAddress && loc != null) {
                                    locationHelper.addressOf(loc)
                                } else null
                                val lines = ImageStamper.buildLines(captureConfig, Date(), loc, address)
                                val stamped = ImageStamper.stamp(context, bmp, captureConfig, lines)
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

    // 원격(리모컨) 촬영 연결.
    // - 볼륨 / 블루투스 셔터 리모컨: MainActivity.dispatchKeyEvent 가 RemoteShutterBus 로 전달
    // - 갤럭시 S펜 버튼: SpenRemoteController(리플렉션) 가 RemoteShutterBus 로 전달
    // 카메라 화면이 사라지면(설정 화면 등) onDispose 로 해제되어 볼륨 키가 정상 동작한다.
    val spenController = remember { SpenRemoteController(context) }
    DisposableEffect(config.remoteShutterEnabled, config.spenRemoteEnabled) {
        val anyRemote = config.remoteShutterEnabled || config.spenRemoteEnabled
        RemoteShutterBus.onTrigger = if (anyRemote) ({ takePhoto() }) else null
        // S펜 버튼도 상당수 기기에서 볼륨 키로 들어오므로, 두 설정 중 하나라도 켜지면 키를 가로챈다.
        RemoteShutterBus.keyEventsEnabled = anyRemote

        if (config.spenRemoteEnabled) spenController.connect() else spenController.disconnect()

        onDispose {
            RemoteShutterBus.onTrigger = null
            RemoteShutterBus.keyEventsEnabled = false
            spenController.disconnect()
        }
    }

    fun applyZoomScale(scaleFactor: Float) {
        val camera = boundCamera ?: return
        val target = nextZoomRatio(
            current = requestedZoomRatio,
            scaleFactor = scaleFactor,
            min = minZoomRatio,
            max = maxZoomRatio
        )
        requestZoom(camera, target)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val (frameWidth, frameHeight) = if (maxWidth / maxHeight > displayAspectRatio) {
            (maxHeight * displayAspectRatio) to maxHeight
        } else {
            maxWidth to (maxWidth / displayAspectRatio)
        }
        // 테두리가 있으면 로고/각인 텍스트가 겹치지 않도록 안전 여백을 추가로 확보한다.
        // ImageStamper의 실제 각인 로직과 동일한 비율(BorderMetrics)을 사용해 결과물과 어긋나지 않게 한다.
        val borderInset = frameWidth * BorderMetrics.contentInsetFraction(config.border, config.borderThickness)

        Box(
            modifier = Modifier
                .size(frameWidth, frameHeight)
                .align(Alignment.Center)
                .pointerInput(boundCamera, minZoomRatio, maxZoomRatio) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        applyZoomScale(gestureZoom)
                    }
                }
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 테두리 미리보기
            if (config.border != StampBorder.NONE) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawStampBorder(config.border, Color(config.borderColor), config.borderThickness)
                }
            }

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
                    fontSize = (config.fontSize * 0.5).sp,
                    fontFamily = config.font.fontFamily(),
                    fontWeight = config.font.fontWeight(),
                    lineHeight = (config.fontSize * 0.6).sp,
                    textAlign = textAlign,
                    modifier = Modifier
                    .align(overlayAlignment)
                    .padding((if (config.aspectRatio == StampAspectRatio.RATIO_1_1) 4.dp else 20.dp) + borderInset)
            )
            }
        }

        if (filterPanelExpanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { filterPanelExpanded = false }
            )
        }

        // 로고 미리보기
        if (config.showLogo && config.logoPosition != LogoPosition.NONE) {
            val logoAlignment = when (config.logoPosition) {
                LogoPosition.TOP_LEFT -> Alignment.TopStart
                LogoPosition.TOP_RIGHT -> Alignment.TopEnd
                LogoPosition.BOTTOM_LEFT -> Alignment.BottomStart
                LogoPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                else -> Alignment.TopEnd
            }
            // 상단 좌/우에는 전환·설정 버튼이 고정으로 떠 있으므로, 로고가 상단일 때는
            // 그 버튼들과 겹치지 않도록 위쪽 여백을 더 준다. 테두리가 있으면 모든 방향에
            // borderInset 만큼 추가로 밀어내 테두리와도 겹치지 않게 한다.
            val logoTopExtra = if (config.logoPosition == LogoPosition.TOP_LEFT ||
                config.logoPosition == LogoPosition.TOP_RIGHT
            ) 68.dp else 0.dp
            val logoPadding = PaddingValues(
                start = 16.dp + borderInset,
                end = 16.dp + borderInset,
                top = 16.dp + logoTopExtra + borderInset,
                bottom = 16.dp + borderInset
            )
            Row(
                modifier = Modifier
                    .align(logoAlignment)
                    .padding(logoPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val logoTint = config.logoColor.tint?.let { Color(it) } ?: Color.Unspecified
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_butterfly_royal),
                    contentDescription = null,
                    tint = logoTint,
                    modifier = Modifier
                        .size((config.logoSize * 0.4).dp)
                        .offset(y = (-4).dp) // 아이콘을 위로 살짝 올림
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Time Stamp",
                    color = Color.White,
                    fontSize = (config.logoSize * 0.3).sp,
                    fontFamily = config.logoFont.fontFamily(),
                    fontWeight = config.logoFont.fontWeight()
                )
                Spacer(Modifier.size(4.dp))
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_butterfly_royal),
                    contentDescription = null,
                    tint = logoTint,
                    modifier = Modifier
                        .size((config.logoSize * 0.4).dp)
                        .graphicsLayer(scaleX = -1f) // 좌우 반전
                        .offset(y = (-4).dp) // 아이콘을 위로 살짝 올림
                )
            }
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

        if (filterPanelExpanded) {
            PhotoFilterPanel(
                selected = config.photoFilter,
                intensity = config.photoFilterIntensity,
                onSelect = { preset -> onChange(config.copy(photoFilter = preset)) },
                onIntensityChange = { value ->
                    onChange(config.copy(photoFilterIntensity = value.coerceIn(0, 100)))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 118.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = (-91).dp)
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraCircleButton(
                text = "✨뷰티",
                active = config.beautyEnabled,
                onClick = { onChange(config.copy(beautyEnabled = !config.beautyEnabled)) }
            )
            PhotoFilterButton(
                selected = config.photoFilter,
                expanded = filterPanelExpanded,
                onClick = { filterPanelExpanded = !filterPanelExpanded }
            )
        }

        CameraCircleButton(
            text = "전환",
            active = false,
            onClick = {
                requestedZoomRatio = 1f
                zoomRatio = 1f
                minZoomRatio = 1f
                maxZoomRatio = 1f
                boundCamera = null
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 70.dp)
                .padding(bottom = 40.dp)
        )

        // 줌 축소/확대 버튼과 CameraX가 보고한 실제 배율
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 118.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomButton(
                label = "−",
                enabled = zoomRatio > minZoomRatio + 0.001f,
                onClick = { applyZoomScale(1f / ZOOM_BUTTON_FACTOR) }
            )
            Text(
                text = String.format(java.util.Locale.US, "%.1f×", zoomRatio),
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            ZoomButton(
                label = "+",
                enabled = zoomRatio < maxZoomRatio - 0.001f,
                onClick = { applyZoomScale(ZOOM_BUTTON_FACTOR) }
            )
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

@Composable
private fun CameraCircleButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFFFF80AB) else Color.White.copy(alpha = 0.85f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = if (text == "전환") 12.sp else 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ZoomButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.95f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.Black.copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 선택된 테두리 템플릿을 미리보기 화면에 그린다.
 * [ImageStamper]의 실제 각인 로직과 동일한 비율([BorderMetrics])을 사용해 결과물과 최대한 일치시킨다.
 */
private fun DrawScope.drawStampBorder(border: StampBorder, color: Color, thickness: Int) {
    val w = size.width
    val h = size.height
    when (border) {
        StampBorder.NONE -> {}
        StampBorder.SIMPLE -> drawSimpleFrame(w, h, color, thickness)
        StampBorder.DOUBLE_LINE -> drawDoubleLineFrame(w, h, color, thickness)
        StampBorder.CORNER_MARKS -> drawCornerMarks(w, h, color, thickness)
    }
}

private fun DrawScope.drawSimpleFrame(w: Float, h: Float, color: Color, thickness: Int) {
    val strokeWidth = w * (thickness / 1000f)
    val inset = strokeWidth / 2f
    drawRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(w - strokeWidth, h - strokeWidth),
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.drawDoubleLineFrame(w: Float, h: Float, color: Color, thickness: Int) {
    val outerStroke = w * (thickness / 1000f)
    val gap = outerStroke * (8f / 12f)
    val innerStroke = outerStroke * (4f / 12f)

    drawRect(
        color = color,
        topLeft = Offset(outerStroke / 2f, outerStroke / 2f),
        size = Size(w - outerStroke, h - outerStroke),
        style = Stroke(width = outerStroke)
    )
    val innerInset = outerStroke + gap + innerStroke / 2f
    drawRect(
        color = color,
        topLeft = Offset(innerInset, innerInset),
        size = Size(w - innerInset * 2, h - innerInset * 2),
        style = Stroke(width = innerStroke)
    )
}

private fun DrawScope.drawCornerMarks(w: Float, h: Float, color: Color, thickness: Int) {
    val strokeWidth = w * (thickness / 1000f)
    val armLength = w * 0.07f
    val margin = w * 0.03f

    // 좌상단
    drawLine(color, Offset(margin, margin + armLength), Offset(margin, margin), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(margin, margin), Offset(margin + armLength, margin), strokeWidth, StrokeCap.Round)
    // 우상단
    drawLine(color, Offset(w - margin - armLength, margin), Offset(w - margin, margin), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(w - margin, margin), Offset(w - margin, margin + armLength), strokeWidth, StrokeCap.Round)
    // 좌하단
    drawLine(color, Offset(margin, h - margin - armLength), Offset(margin, h - margin), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(margin, h - margin), Offset(margin + armLength, h - margin), strokeWidth, StrokeCap.Round)
    // 우하단
    drawLine(color, Offset(w - margin - armLength, h - margin), Offset(w - margin, h - margin), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(w - margin, h - margin - armLength), Offset(w - margin, h - margin), strokeWidth, StrokeCap.Round)
}
