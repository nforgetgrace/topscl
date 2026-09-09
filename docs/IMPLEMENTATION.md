# Implementation and validation plan

1. Build a dependency-free native Android application and original Korean UI.
2. Disclose accessibility access and require local consent plus explicit system enabling.
3. Attach a small TYPE_ACCESSIBILITY_OVERLAY trigger; distinguish taps/drags/multitouch; preserve native edges and provide shade action for drags within the trigger.
4. Prefer semantic scroll-to-position, then bounded backward/up actions; opt-in gesture fallback only within an identified scrollable viewport. Reacquire nodes and stop on changed app/window, lock, pause, cancellation, timeout, or lack of progress.
5. Use OS-bound accessibility lifecycle, cleaned-up listeners/callbacks, persistent preferences, actionable actual health state, exclusion controls, Quick Settings tile.
6. Verify compile + lint + core tests + emulator install, UI screenshots, external list/grid/WebView fixture, pause/exclusion/context switch, service process restart, rotation/screen off.

No general claim of universal app compatibility or immunity to Android force-stop. APK is a locally signed debug build for installation/testing, not a Play Store release.

## 구현 결과

- `TopTapService`는 접근성 서비스 연결 수명에 맞춰 작은 상태 표시줄 영역을 생성하고 해제합니다. 창 변경 이벤트와 화면 상태 방송으로 갱신하며 대기 중 주기적인 화면 탐색이나 keep-alive 작업은 없습니다. 잠금·권한 철회·일시정지·제외 앱에서는 영역을 제거합니다.
- `ScrollEngine`은 한 번에 하나의 실행만 허용합니다. 최대 250개 노드를 탐색해 큰 세로 스크롤 영역을 선택하고, 매 단계 노드를 다시 확인합니다. 선택한 앱·창·영역이 바뀌면 종료합니다. 화면 문자열을 읽거나 저장하지 않습니다.
- 첫 행으로 이동하는 동작을 우선하고, 지원되는 경우 Android 15의 무한 스크롤 양 인자, 그다음 위쪽 페이지/스크롤 동작을 사용합니다. WebView가 위쪽 동작을 목록에 누락해도 처리할 수 있으므로, 식별된 WebView에는 위쪽 의미 동작을 직접 요청합니다. 다른 제어 위젯에는 이 예외를 적용하지 않습니다.
- WebView의 애니메이션을 기다리도록 단계 사이에 500ms, 일반 위젯에는 170ms 간격을 둡니다. 시간 제한·진행 없음·재활성화·화면 조작 시 새 동작 전송을 멈춥니다. 이미 시작된 화면의 짧은 애니메이션은 완료될 수 있습니다.
- 호환 스와이프는 기본 꺼짐이며, 사용자가 켜도 식별된 스크롤 영역 안에서 최대 네 번만 실행합니다. 의미 스크롤이 이동한 뒤 경계에 도달하면 스와이프로 전환하지 않습니다.
- 상태 표시줄 아래 드래그는 시스템 알림창/빠른 설정 동작으로 변환합니다. Android 15/16에서 시스템이 가장자리 제스처를 넘겨받으며 보내는 취소 이벤트도 처리합니다.
- UI는 네이티브 Java와 벡터/Canvas로 작성했습니다. 런타임 외부 라이브러리가 없고, 배포 앱과 별도 패키지인 `fixture`로 앱 간 동작을 검증합니다.

실행 가능한 검증 명령, 결과 및 미검증 범위는 [VERIFICATION.md](VERIFICATION.md)에 기록합니다.

## Sources checked
- https://play.google.com/store/apps/details?id=com.scroll.scrolltop.backtotop&hl=ko
- https://developer.android.com/guide/topics/ui/accessibility/service
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY
- https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction#ACTION_SCROLL_TO_POSITION
- https://www.samsung.com/us/support/galaxy-battery/optimization/
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
