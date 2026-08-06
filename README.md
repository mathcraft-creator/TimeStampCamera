# 타임스탬프 카메라 (광고 없음)

날짜·시간·GPS 좌표·주소를 사진에 각인해 주는 안드로이드 앱입니다.
Kotlin + CameraX + Jetpack Compose 로 만들었고, **광고가 전혀 없습니다.**
갤럭시 노트20 울트라를 포함한 안드로이드 10(API 29) 이상 기기에서 동작합니다.

## 주요 기능

- 각인 항목을 **켜고 끌 수 있음**: 날짜·시간 / GPS 좌표 / 주소 / 메모(학원명 등)
- 각인 **위치** 선택: 좌측상단 · 우측상단 · 좌측하단 · 우측하단 · 하단중앙
- 각인 **글자 크기** 조절 (사진 크기에 비례, 2~8%)
- 각인 **글자 색** 선택 (흰/노랑/주황/빨강/초록/하늘/검정)
- 각인 **글꼴** 선택 (고딕 / 고딕 굵게 / 명조 / 고정폭)
- 촬영하면 갤러리의 `Pictures/TimestampCamera` 폴더에 자동 저장

설정은 카메라 화면 오른쪽 위 톱니바퀴(⚙️) 버튼에서 바꿉니다. 바꾼 값은 자동 저장됩니다.

---

## APK 만들기 (GitHub Actions 자동 빌드)

PC에 아무것도 설치하지 않고, GitHub가 클라우드에서 APK를 만들어 줍니다.

### 1. GitHub 저장소 만들기
1. https://github.com 에 로그인 → 우측 상단 `+` → **New repository**
2. 저장소 이름(예: `timestamp-camera`) 입력 → **Private** 선택해도 됩니다 → **Create repository**

### 2. 이 폴더의 파일들을 저장소에 올리기
가장 쉬운 방법(웹 업로드):
1. 만든 저장소 페이지에서 **Add file → Upload files**
2. 이 폴더 안의 **모든 파일과 폴더**를 통째로 끌어다 놓기
   (`app/`, `.github/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` 등 전부)
3. 아래 **Commit changes** 클릭

> `.github/workflows/build.yml` 파일이 반드시 올라가야 자동 빌드가 됩니다.

### 3. APK 빌드 실행
- main 브랜치에 파일을 올리면 **자동으로 빌드가 시작**됩니다.
- 저장소 상단 **Actions** 탭 → `Build APK` 워크플로 클릭 → 초록색 체크(✓)가 뜰 때까지 기다립니다(보통 3~6분).
- 수동으로 다시 돌리려면 Actions 탭에서 **Run workflow** 버튼을 누르면 됩니다.

### 4. APK 내려받기
1. 완료된 빌드(체크 표시)를 클릭
2. 아래 **Artifacts** 항목의 **`TimestampCamera-debug-apk`** 를 클릭해 zip 다운로드
3. zip 을 풀면 `app-debug.apk` 파일이 나옵니다.

### 5. 노트20 울트라에 설치
1. APK 파일을 폰으로 옮깁니다(카톡 나에게 보내기, 구글 드라이브, USB 등).
2. 파일을 탭해서 설치 → "출처를 알 수 없는 앱" 경고가 뜨면 **이 출처 허용** 후 설치.
3. 앱 실행 → **카메라 권한 허용**, GPS/주소를 쓰려면 **위치 권한도 허용**.

끝입니다. 이제 광고 없이 사용하시면 됩니다.

---

## 참고 사항

- **위치·주소**는 위치 권한이 있어야 각인됩니다. 처음 켰을 때 위성 신호를 잡는 데 몇 초 걸릴 수 있어, 실외에서 잠깐 기다렸다 찍으면 정확합니다.
- 주소 변환(좌표 → 한글 주소)은 인터넷 연결과 기기 상태에 따라 실패할 수 있으며, 실패 시 그 줄은 생략됩니다.
- 이 빌드는 **디버그 APK**라 별도 서명 없이 바로 설치됩니다. 개인용으로 충분합니다.

## 나중에 안드로이드 스튜디오에서 직접 열고 싶다면
Android Studio 에서 **Open** → 이 폴더 선택 → 자동으로 Gradle 동기화됩니다.
(그때는 Android Studio가 gradle wrapper를 자동 생성합니다.)

## 폴더 구조
```
app/                      앱 소스
  src/main/
    java/com/mathcraft/timestampcamera/
      MainActivity.kt     권한 처리 + 화면 전환
      CameraScreen.kt     카메라 미리보기·촬영·실시간 각인 미리보기
      SettingsScreen.kt   각인 항목/위치/크기/색/글꼴 설정 메뉴
      StampConfig.kt      설정 데이터 + 저장(SharedPreferences)
      ImageStamper.kt     사진에 글자 각인
      LocationHelper.kt   위치·주소 획득
      CameraUtils.kt      이미지 변환·갤러리 저장
    res/                  아이콘·문자열·테마
  build.gradle.kts        앱 의존성/빌드 설정
.github/workflows/build.yml  APK 자동 빌드
```
