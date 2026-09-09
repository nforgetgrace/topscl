# 탑탭 · TopTap

갤럭시 화면 상단을 톡 눌러 현재 앱의 목록을 맨 위로 이동하는 네이티브 안드로이드 앱입니다. 한국어 UI와 오리지널 벡터 아이콘을 포함합니다.

[설치용 APK](dist/TopTap-1.0.0-debug.apk) · [검증 기록](docs/VERIFICATION.md) · [앱 화면](docs/screenshots/home.png)

## 설치와 사용

1. `dist/TopTap-1.0.0-debug.apk`를 휴대폰에 옮겨 설치합니다. 개인 기기 테스트용 서명 APK입니다.
2. 앱을 열고 **탑탭 시작하기**를 누릅니다. 접근성 사용 설명을 읽고 동의한 뒤 Android 설정에서 **탑탭**을 직접 켭니다.
3. 긴 목록을 내려 본 뒤 **왼쪽 상단의 파란 선**을 누릅니다. 이동 중 같은 동작을 반복하면 멈춥니다. 두 번 누르기 모드에서는 두 번 눌러 시작/중지합니다.
4. 앱에서 **긴 목록에서 연습하기**로 먼저 확인할 수 있습니다.
5. 갤럭시에서는 앱 정보 → 배터리 → **제한 없음**, 설정 → 배터리 → 백그라운드 사용 제한 → **절전 예외 앱**에 탑탭을 추가하면 절전으로 중단될 가능성을 줄일 수 있습니다. One UI마다 명칭이 다릅니다.

직접 설치한 APK의 접근성 메뉴가 차단되면 앱 정보의 오른쪽 위 메뉴에서 **제한된 설정 허용**이 필요할 수 있습니다. 확인한 출처의 APK에만 허용하세요.

## 기능

- 상단 터치 위치·너비, 한 번/두 번 누르기, 표시선, 진동 설정.
- 앱이 지원하면 첫 행으로 바로 이동하고, 그 외에는 위로 스크롤을 반복.
- 최대 실행 시간, 진행 없음 감지, 다시 눌러 중지, 앱·창 전환 및 잠금 시 중지.
- 호환 스와이프는 사용자가 켰을 때만 식별된 스크롤 영역에서 제한적으로 실행.
- 앱별 제외, 일시정지, 빠른 설정의 탑탭 타일.
- 실제 접근성 연결 상태와 갤럭시 절전 설정 안내.
- 인터넷·사진·연락처 권한, 광고, 분석 SDK, 서드파티 런타임 라이브러리 없음.

## 지원 범위와 한계

- 최소 Android 8.0(API 26), compile/target SDK 36. 자동 검증 결과는 [검증 보고서](docs/VERIFICATION.md)를 참고하세요.
- Android에는 다른 모든 앱에 ‘맨 위로’를 강제하는 공통 API가 없습니다. 보안 화면, 게임, 일부 자체 렌더링 화면, 앱이 접근성 스크롤 정보를 제공하지 않는 영역은 지원되지 않을 수 있습니다.
- 상태 표시줄 전체를 가로채지 않습니다. 기본값은 작은 왼쪽 영역이며, 나머지 영역은 시스템 제스처용으로 남겨 둡니다. 터치 영역 안의 아래쪽 드래그는 알림창을, 화면 오른쪽 1/3의 영역에서는 빠른 설정을 여는 시스템 동작으로 변환합니다. 원래 드래그 이벤트를 재전송하는 방식은 아닙니다. Samsung One UI에서의 세부 제스처 동작은 실기기 검증이 필요합니다.
- 카메라 구멍과 겹치면 터치 영역을 왼쪽/오른쪽으로 옮기세요. 외부 디스플레이와 폴더블 전환은 아직 검증하지 않았습니다.
- 아주 긴 목록은 제한 시간(기본 12초, 최대 20초)에 도달하면 멈춥니다. 다시 누르면 이어서 이동합니다.
- 호환 스와이프는 일부 앱에서 새로고침 등 앱 고유 제스처를 유발할 수 있어 기본값이 꺼짐입니다. 한 번에 최대 4회이며 화면·앱이 바뀌거나 취소되면 새 제스처를 보내지 않습니다. 이미 전송한 짧은 제스처는 완료될 수 있습니다.
- 시스템이 관리하는 접근성 서비스이며 무한 재시작, wake lock, 주기적 keep-alive를 사용하지 않습니다. **강제 종료·권한 철회·제조사 절전 정책을 무시할 수 없습니다.** 재연결이 필요하면 앱의 도움말을 확인하세요.

## 개발

JDK 17 이상(검증 JDK 21), Android SDK 36, Build Tools 36.0.0을 준비합니다.

```sh
# local.properties에 실제 SDK 위치 지정
printf 'sdk.dir=/your/android/sdk\n' > local.properties
./gradlew :app:assembleDebug :fixture:assembleDebug :app:lintDebug :fixture:lintDebug
sh scripts/test-core.sh
```

또는 `sh scripts/build.sh`로 빌드·정적 검사·핵심 테스트와 `dist/` APK 복사를 한 번에 실행합니다. macOS에서는 설치된 JDK 17 이상을 선택합니다.

APK 출력: `app/build/outputs/apk/debug/app-debug.apk`.

재현 가능한 별도 테스트 앱은 `fixture/`에 있습니다. 사용자 배포 APK에는 포함되지 않습니다.

```sh
# 지정한 테스트 에뮬레이터의 TopTap 앱 데이터/설정을 바꿉니다.
# 개인 기기를 지정하면 실행을 거부합니다.
python3 scripts/e2e.py --serial emulator-5580 --adb /your/android/sdk/platform-tools/adb
```

핵심 구현은 `TopTapService.java`(영역·생명주기), `ScrollEngine.java`(스크롤·중지), `TapRecognizer.java`(터치 판별), `MainActivity.java`(설정·안내)입니다. 서비스 상태는 디스크 설정이 아닌 실제 연결 콜백으로 표시합니다. 디버그 빌드는 스크롤 상태 메시지만 Logcat `TopTap` 태그로 기록하며 화면 글·사진·입력값을 기록하지 않습니다.

## 참고한 공식 문서

- [Android 접근성 서비스](https://developer.android.com/guide/topics/ui/accessibility/service): 화면 구조 조회와 제스처 실행.
- [AccessibilityService 생명주기](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService): 사용자가 설정에서 켜고 시스템이 연결을 관리.
- [Accessibility overlay](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY): 별도 SYSTEM_ALERT_WINDOW 권한 없이 접근성 서비스에 연결된 영역 사용.
- [Scroll to position](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction#ACTION_SCROLL_TO_POSITION): 위젯의 행/열 스크롤 동작.
- [스크롤 양 지정](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT): 지원 위젯에서 한 번에 끝까지 이동.
- [Samsung 절전 예외 설정](https://www.samsung.com/us/support/galaxy-battery/optimization/): 제조사 백그라운드 제한 안내.

참고 앱은 [Tap Scroll - Perfect Scroll](https://play.google.com/store/apps/details?id=com.scroll.scrolltop.backtotop&hl=ko)이며 코드·브랜드·디자인은 복제하지 않았습니다.
