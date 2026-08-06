package com.mathcraft.timestampcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Google Play 서비스 없이 안드로이드 기본 LocationManager 만 사용한다.
 * 화면이 열려 있는 동안 위치 업데이트를 받아 최신 위치를 들고 있는다.
 */
class LocationHelper(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    private var current: Location? = null

    private val listener = LocationListener { location -> current = location }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                if (!locationManager.isProviderEnabled(provider)) continue
                locationManager.requestLocationUpdates(
                    provider,
                    2000L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                val last = locationManager.getLastKnownLocation(provider)
                if (last != null && (current == null || last.time > (current?.time ?: 0L))) {
                    current = last
                }
            } catch (e: SecurityException) {
                // 권한이 회수된 경우 무시
            } catch (e: IllegalArgumentException) {
                // 지원하지 않는 provider 무시
            }
        }
    }

    fun stop() {
        try {
            locationManager.removeUpdates(listener)
        } catch (e: Exception) {
            // 무시
        }
    }

    /** 현재까지 확보한 가장 최신 위치 */
    fun current(): Location? = current

    /** 좌표를 한글 주소 문자열로 변환. 실패하면 null. */
    fun addressOf(location: Location): String? {
        return try {
            val geocoder = Geocoder(appContext, Locale.KOREA)
            @Suppress("DEPRECATION")
            val results: List<Address>? =
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val address = results?.firstOrNull() ?: return null
            address.getAddressLine(0)
                ?: listOfNotNull(
                    address.adminArea,
                    address.locality,
                    address.subLocality,
                    address.thoroughfare
                ).joinToString(" ").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
