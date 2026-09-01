# 기본 규칙.

# 삼성 S펜 Pen Remote SDK (app/libs/spenremote-*.jar).
# SpenRemoteController 가 리플렉션으로 접근하므로 이름을 보존한다.
-keep class com.samsung.android.sdk.penremote.** { *; }
# SDK 가 참조하지만 이 앱/SDK 배포본에 포함되지 않는 삼성 프레임워크 클래스 경고 무시.
-dontwarn com.samsung.android.sdk.**
-dontwarn com.samsung.android.feature.**
