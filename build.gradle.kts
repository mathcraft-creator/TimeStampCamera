// 최상위 빌드 파일 — 하위 모듈에서 사용할 플러그인 버전을 선언만 한다.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
