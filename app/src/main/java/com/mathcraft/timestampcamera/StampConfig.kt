package com.mathcraft.timestampcamera

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

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
    SERIF_BOLD("명조 굵게"),
    MONO("고정폭");

    fun typeface(): Typeface = when (this) {
        SANS -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        SANS_BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        SERIF -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        SERIF_BOLD -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    fun fontFamily(): FontFamily = when (this) {
        SANS -> FontFamily.SansSerif
        SANS_BOLD -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        SERIF_BOLD -> FontFamily.Serif
        MONO -> FontFamily.Monospace
    }

    fun fontWeight(): FontWeight = when (this) {
        SANS_BOLD, SERIF_BOLD -> FontWeight.Bold
        else -> FontWeight.Normal
    }
}

/** 날짜/시간 템플릿 */
enum class StampTemplate(val label: String, val format: String) {
    YMD_HMS("2024-08-10 15:30:20", "yyyy-MM-dd HH:mm:ss"),
    YMD_HM("2024-08-10 15:30", "yyyy-MM-dd HH:mm"),
    MD_HM("08-10 15:30", "MM-dd HH:mm"),
    YMD_KR("2024년 8월 10일", "yyyy년 MM월 dd일"),
    YMD_DOT("2024.08.10", "yyyy.MM.dd"),
    HMS("15:30:20", "HH:mm:ss"),
    KR_MULTILINE("2024년 8월 10일(토)\n오후 7시 22분", "yyyy년 M월 d일(E)\na h시 m분")
}

/** 사진 비율 */
enum class StampAspectRatio(val label: String, val value: Float) {
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_1_1("1:1", 1f)
}

/** 로고 위치 */
enum class LogoPosition(val label: String) {
    TOP_LEFT("좌측 상단"),
    TOP_RIGHT("우측 상단"),
    BOTTOM_LEFT("좌측 하단"),
    BOTTOM_RIGHT("우측 하단"),
    NONE("표시 안함")
}

/**
 * 로고 아이콘 색상.
 * 아이콘 이미지 자체는 [R.drawable.ic_butterfly_royal] 하나만 사용하고,
 * tint 가 null 이면 원본(골드 그라데이션)을 그대로, 값이 있으면 실루엣에 해당 색을 입힌다.
 */
enum class StampLogoColor(val label: String, val tint: Int?) {
    GOLD("골드(원본)", null),
    WHITE("화이트", Color.WHITE),
    YELLOW("옐로우", Color.rgb(255, 213, 79)),
    PINK("핑크", Color.rgb(255, 105, 180)),
    BLUE("블루", Color.rgb(66, 165, 245))
}

/**
 * 사진 테두리 템플릿(모양).
 * 색상·굵기는 템플릿과 별개로 [StampConfig.borderColor] / [StampConfig.borderThickness] 로 조절한다.
 */
enum class StampBorder(val label: String) {
    NONE("없음"),
    SIMPLE("심플 라인"),
    DOUBLE_LINE("더블 라인"),
    CORNER_MARKS("코너 브라켓")
}

/** 테두리 색상 프리셋 (라벨 + ARGB 색상 값) */
enum class StampBorderColor(val label: String, val color: Int) {
    GOLD("골드", Color.rgb(212, 175, 55)),
    WHITE("화이트", Color.WHITE),
    BLACK("블랙", Color.BLACK),
    YELLOW("옐로우", Color.rgb(255, 213, 79)),
    PINK("핑크", Color.rgb(255, 105, 180)),
    BLUE("블루", Color.rgb(66, 165, 245)),
    RED("레드", Color.rgb(244, 67, 54))
}

/**
 * 테두리 두께·모양에 따라 로고/각인 텍스트가 확보해야 할 안전 여백을 계산한다.
 * 값은 이미지 가로폭에 대한 비율(fraction)이며, [ImageStamper]의 실제 각인과
 * [CameraScreen]의 실시간 미리보기가 이 함수 하나를 공유해 서로 어긋나지 않게 한다.
 */
object BorderMetrics {
    /** 슬라이더 값(thousandths of width)을 실제 굵기 비율로 변환 */
    private fun strokeFraction(thickness: Int): Float = thickness / 1000f

    fun contentInsetFraction(border: StampBorder, thickness: Int): Float {
        val t = strokeFraction(thickness)
        return when (border) {
            StampBorder.NONE -> 0f
            StampBorder.SIMPLE -> t * 1.8f
            StampBorder.DOUBLE_LINE -> {
                val outer = t
                val gap = outer * (8f / 12f)
                val inner = outer * (4f / 12f)
                (outer + gap + inner) * 1.3f
            }
            StampBorder.CORNER_MARKS -> 0.03f + 0.07f + t
        }
    }
}

/**
 * 사용자가 설정한 각인 옵션 전체.
 * fontSize 는 포인트(PT) 단위이다.
 */
data class StampConfig(
    val showDateTime: Boolean = true,
    val showGps: Boolean = false,
    val showAddress: Boolean = false,
    val customText: String = "",
    val position: StampPosition = StampPosition.BOTTOM_RIGHT,
    val color: StampColorOption = StampColorOption.YELLOW,
    val font: StampFontOption = StampFontOption.SANS_BOLD,
    val fontSize: Int = 40,
    val template: StampTemplate = StampTemplate.KR_MULTILINE,
    val aspectRatio: StampAspectRatio = StampAspectRatio.RATIO_3_4,
    val showLogo: Boolean = true,
    val logoPosition: LogoPosition = LogoPosition.TOP_RIGHT,
    val logoSize: Int = 50,
    val logoFont: StampFontOption = StampFontOption.MONO,
    val logoColor: StampLogoColor = StampLogoColor.GOLD,
    val border: StampBorder = StampBorder.NONE,
    val borderColor: Int = StampBorderColor.GOLD.color,
    val borderThickness: Int = 18,
    val beautyEnabled: Boolean = false,
    val beautySmooth: Int = 50,
    val beautyBrighten: Int = 40,
    val photoFilter: PhotoFilterPreset = PhotoFilterPreset.ORIGINAL,
    val photoFilterIntensity: Int = 100
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
        fontSize = prefs.getInt(KEY_SIZE_PT, 40),
        template = safeEnum(prefs.getString(KEY_TEMPLATE, null), StampTemplate.KR_MULTILINE),
        aspectRatio = safeEnum(prefs.getString(KEY_ASPECT_RATIO, null), StampAspectRatio.RATIO_3_4),
        showLogo = prefs.getBoolean(KEY_SHOW_LOGO, true),
        logoPosition = safeEnum(prefs.getString(KEY_LOGO_POSITION, null), LogoPosition.TOP_RIGHT),
        logoSize = prefs.getInt(KEY_LOGO_SIZE, 50),
        logoFont = safeEnum(prefs.getString(KEY_LOGO_FONT, null), StampFontOption.MONO),
        logoColor = safeEnum(prefs.getString(KEY_LOGO_COLOR, null), StampLogoColor.GOLD),
        border = safeEnum(prefs.getString(KEY_BORDER, null), StampBorder.NONE),
        borderColor = prefs.getInt(KEY_BORDER_COLOR, StampBorderColor.GOLD.color),
        borderThickness = prefs.getInt(KEY_BORDER_THICKNESS, 18),
        beautyEnabled = prefs.getBoolean(KEY_BEAUTY_ENABLED, false),
        beautySmooth = prefs.getInt(KEY_BEAUTY_SMOOTH, 50),
        beautyBrighten = prefs.getInt(KEY_BEAUTY_BRIGHTEN, 40),
        photoFilter = PhotoFilterPreset.fromId(prefs.getString(KEY_PHOTO_FILTER_ID, null)),
        photoFilterIntensity = prefs.getInt(KEY_PHOTO_FILTER_INTENSITY, 100).coerceIn(0, 100)
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
            .putInt(KEY_SIZE_PT, c.fontSize)
            .putString(KEY_TEMPLATE, c.template.name)
            .putString(KEY_ASPECT_RATIO, c.aspectRatio.name)
            .putBoolean(KEY_SHOW_LOGO, c.showLogo)
            .putString(KEY_LOGO_POSITION, c.logoPosition.name)
            .putInt(KEY_LOGO_SIZE, c.logoSize)
            .putString(KEY_LOGO_FONT, c.logoFont.name)
            .putString(KEY_LOGO_COLOR, c.logoColor.name)
            .putString(KEY_BORDER, c.border.name)
            .putInt(KEY_BORDER_COLOR, c.borderColor)
            .putInt(KEY_BORDER_THICKNESS, c.borderThickness)
            .putBoolean(KEY_BEAUTY_ENABLED, c.beautyEnabled)
            .putInt(KEY_BEAUTY_SMOOTH, c.beautySmooth)
            .putInt(KEY_BEAUTY_BRIGHTEN, c.beautyBrighten)
            .putString(KEY_PHOTO_FILTER_ID, c.photoFilter.id)
            .putInt(KEY_PHOTO_FILTER_INTENSITY, c.photoFilterIntensity.coerceIn(0, 100))
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
        private const val KEY_SIZE_PT = "fontSizePt" // 기존 sizePercent 와 겹치지 않게 변경
        private const val KEY_TEMPLATE = "stampTemplate"
        private const val KEY_ASPECT_RATIO = "aspectRatio"
        private const val KEY_SHOW_LOGO = "showLogo"
        private const val KEY_LOGO_POSITION = "logoPosition"
        private const val KEY_LOGO_SIZE = "logoSize"
        private const val KEY_LOGO_FONT = "logoFont"
        private const val KEY_LOGO_COLOR = "logoColor"
        private const val KEY_BORDER = "border"
        private const val KEY_BORDER_COLOR = "borderColor"
        private const val KEY_BORDER_THICKNESS = "borderThickness"
        private const val KEY_BEAUTY_ENABLED = "beautyEnabled"
        private const val KEY_BEAUTY_SMOOTH = "beautySmooth"
        private const val KEY_BEAUTY_BRIGHTEN = "beautyBrighten"
        private const val KEY_PHOTO_FILTER_ID = "photoFilterId"
        private const val KEY_PHOTO_FILTER_INTENSITY = "photoFilterIntensity"
    }
}
