package com.mathcraft.timestampcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color.Black) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    var config by remember { mutableStateOf(repo.load()) }
    var showSettings by remember { mutableStateOf(false) }

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasCamera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    fun hasLocationPermission() =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCamera = result[Manifest.permission.CAMERA] ?: hasCamera
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun requestCameraPermission() {
        cameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // 처음 실행 시 카메라 권한이 없으면 바로 요청
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasCamera) requestCameraPermission()
    }

    androidx.compose.runtime.LaunchedEffect(hasCamera, config.showGps, config.showAddress) {
        if (hasCamera && (config.showGps || config.showAddress) && !hasLocationPermission()) {
            requestLocationPermission()
        }
    }

    when {
        showSettings -> SettingsScreen(
            config = config,
            onChange = {
                config = it
                repo.save(it)
            },
            onBack = { showSettings = false }
        )

        hasCamera -> CameraScreen(
            config = config,
            onOpenSettings = { showSettings = true },
            onChange = {
                config = it
                repo.save(it)
            }
        )

        else -> PermissionPrompt(onRequest = { requestCameraPermission() })
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "카메라 사용 권한이 필요합니다.\n사진에 위치·주소를 각인하려면 위치 권한도 허용해 주세요.",
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("권한 허용하기")
        }
    }
}
