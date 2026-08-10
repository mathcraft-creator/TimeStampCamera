# 각인 설정 고도화 계획

사용자의 요청에 따라 글자 크기 단위를 %에서 포인트(PT)로 변경하고, 날짜/시간 표시 템플릿과 다양한 글꼴 선택 기능을 추가합니다.

## Proposed Changes

### [Component] 데이터 모델 및 저장소 (`StampConfig.kt`)

#### [MODIFY] [StampConfig.kt](file:///D:/codex/TimeStampCamera/app/src/main/java/com/mathcraft/timestampcamera/StampConfig.kt)
- `StampConfig`의 `sizePercent: Int`를 `fontSize: Int`로 변경합니다. (기본값: 40)
- 날짜/시간 형식을 정의하는 `StampTemplate` enum을 추가합니다.
- `StampFontOption`에 더 다양한 시스템 글꼴 옵션을 추가합니다.
- `SettingsRepository`에서 변경된 필드들을 저장/복원하도록 수정합니다.

### [Component] 각인 로직 (`ImageStamper.kt`)

#### [MODIFY] [ImageStamper.kt](file:///D:/codex/TimeStampCamera/app/src/main/java/com/mathcraft/timestampcamera/ImageStamper.kt)
- `StampTemplate`에 따라 `SimpleDateFormat`을 동적으로 생성하여 날짜를 포맷팅합니다.
- `fontSize`를 실제 그리기 시 `textSize`로 사용합니다. (해상도에 상관없이 절대적인 크기를 제공하거나, 특정 기준에 맞춰 스케일링하는 방식 검토)
    - 사용자가 "포인트"를 원하므로, 직관적인 숫자 값(예: 20~100)을 제공합니다.

### [Component] 설정 화면 UI (`SettingsScreen.kt`)

#### [MODIFY] [SettingsScreen.kt](file:///D:/codex/TimeStampCamera/app/src/main/java/com/mathcraft/timestampcamera/SettingsScreen.kt)
- 글자 크기 슬라이더를 포인트 단위(20~150)로 변경합니다.
- 날짜/시간 템플릿을 선택할 수 있는 UI 세션을 추가합니다.
- 확장된 글꼴 옵션을 선택할 수 있도록 ChipRow를 업데이트합니다.

## Verification Plan

### Automated Tests
- N/A (UI 및 그래픽 작업 위주이므로 수동 확인 위주)

### Manual Verification
- 설정 화면에서 글자 크기를 변경했을 때 미리보기(또는 실제 촬영물)의 글자 크기가 의도대로 변하는지 확인.
- 다양한 템플릿을 선택했을 때 날짜 표시 형식이 바뀌는지 확인.
- 글꼴 변경 시 텍스트 스타일이 적용되는지 확인.
- 앱 재시작 후에도 설정값이 유지되는지 확인.
