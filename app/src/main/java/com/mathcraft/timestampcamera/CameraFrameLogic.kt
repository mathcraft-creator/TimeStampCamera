package com.mathcraft.timestampcamera

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
): Float = (current * scaleFactor).coerceIn(min, max)
