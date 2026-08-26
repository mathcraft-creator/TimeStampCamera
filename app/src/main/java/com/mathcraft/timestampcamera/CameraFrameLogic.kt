package com.mathcraft.timestampcamera

const val ZOOM_BUTTON_FACTOR = 1.25f

fun frameAspectRatio(ratio: StampAspectRatio, isLandscape: Boolean): Float =
    if (isLandscape && ratio != StampAspectRatio.RATIO_1_1) {
        1f / ratio.value
    } else {
        ratio.value
    }

fun nextZoomRatio(
    current: Float,
    scaleFactor: Float,
    min: Float,
    max: Float
): Float {
    require(scaleFactor > 0f) { "zoom scale factor must be positive" }
    require(min <= max) { "min zoom must not exceed max zoom" }
    return (current * scaleFactor).coerceIn(min, max)
}
