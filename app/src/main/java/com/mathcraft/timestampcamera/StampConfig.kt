package com.mathcraft.timestampcamera

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

/** 각인 위치 */
enum class StampPosition(val label: String) {
    TOP_LEFT("좌측 상단"),
    TOP_RIGHT("우측 상단"),
    BOTTOM_LEFT("좌측 하단"),
    BOTTOM_RIGHT("우측 하단"),
    BOTTOM_CENTER("하단 중앙")
}

/** 각인 글자 색 */
enum class StampColorOption(val label: String, val color: Int) {
    WHITE("흰색", Color.WHITE),
    YELLOW("노란색", Color.rgb(255, 235, 59)),
    ORANGE("주황색", Color.rgb(255, 152, 0)),
    RED("빨간색", Color.rgb(244, 67, 54)),
    GREEN("초록색", Color.rgb(76, 175, 80)),
    CYAN("하늘색", Color.rgb(0, 229, 255)),
    BLACK("검정색", Color.BLACK)
}

/** 각인 글꼴 */
enum class StampFontOption(val label: String) {
    SANS("기본(고딕)"),
    SANS_BOLD("고딕 굵게"),
    SERIF("명조"),
    MONO("고정폭");

    fun typeface(): Typeface = when (this) {
        SANS -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        SANS_BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        SERIF -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
}

/**
 * 사용자가 설정한 각인 옵션 전체.
 * sizePercent 는 사진 가로 폭 대비 글자 크기 비율(%) 이라 해상도가 달라도 비율이 유지된다.
 */
data class StampConfig(
    val showDateTime: Boolean = true,
    val showGps: Boolean = false,
    val showAddress: Boolean = false,
    val customText: String = "",
    val position: StampPosition = StampPosition.BOTTOM_RIGHT,
    val color: StampColorOption = StampColorOption.YELLOW,
    val font: StampFontOption = StampFontOption.SANS_BOLD,
    val sizePercent: Int = 4
)

/** SharedPreferences 로 설정을 저장/복원한다. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("stamp_settings", Context.MODE_PRIVATE)

    fun load(): StampConfig = StampConfig(
        showDateTime = prefs.getBoolean(KEY_DATETIME, true),
        showGps = prefs.getBoolean(KEY_GPS, false),
        showAddress = prefs.getBoolean(KEY_ADDRESS, false),
        customText = prefs.getString(KEY_CUSTOM, "") ?: "",
        position = safeEnum(prefs.getString(KEY_POSITION, null), StampPosition.BOTTOM_RIGHT),
        color = safeEnum(prefs.getString(KEY_COLOR, null), StampColorOption.YELLOW),
        font = safeEnum(prefs.getString(KEY_FONT, null), StampFontOption.SANS_BOLD),
        sizePercent = prefs.getInt(KEY_SIZE, 4)
    )

    fun save(c: StampConfig) {
        prefs.edit()
            .putBoolean(KEY_DATETIME, c.showDateTime)
            .putBoolean(KEY_GPS, c.showGps)
            .putBoolean(KEY_ADDRESS, c.showAddress)
            .putString(KEY_CUSTOM, c.customText)
            .putString(KEY_POSITION, c.position.name)
            .putString(KEY_COLOR, c.color.name)
            .putString(KEY_FONT, c.font.name)
            .putInt(KEY_SIZE, c.sizePercent)
            .apply()
    }

    private inline fun <reified T : Enum<T>> safeEnum(value: String?, default: T): T =
        try {
            if (value == null) default else enumValueOf<T>(value)
        } catch (e: IllegalArgumentException) {
            default
        }

    companion object {
        private const val KEY_DATETIME = "showDateTime"
        private const val KEY_GPS = "showGps"
        private const val KEY_ADDRESS = "showAddress"
        private const val KEY_CUSTOM = "customText"
        private const val KEY_POSITION = "position"
        private const val KEY_COLOR = "color"
        private const val KEY_FONT = "font"
        private const val KEY_SIZE = "sizePercent"
    }
}
