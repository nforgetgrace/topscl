# 구현

## 상태바와 수명

- `TopTapService`는 Android가 연결하는 접근성 서비스입니다. 상태바 전체 너비를 사용하며, 원터치만 지원합니다. `TriggerView`는 배경·리플·표시선을 그리지 않습니다. 동작 중에도 색이 바뀌지 않습니다.
- 아래로 드래그하면 시스템 알림창을 열고, 화면 오른쪽 1/3에서 드래그하면 빠른 설정을 엽니다. 길게 누르기·다중 터치·가로 이동은 상단 이동으로 처리하지 않습니다.
- 상태바가 숨겨진 전체 화면에서는 터치를 통과시키면서 창 여백 정보의 갱신을 계속 받습니다. 상태바가 복구되거나 활성 창이 바뀌면 영역을 다시 맞춥니다. 잠금·일시정지·제외 앱·권한 허용 창에서는 영역을 제거합니다.
- `ActiveWindow`는 일시적으로 비어 있는 활성 화면 캐시를 보완합니다. 시스템 창 너머의 앱을 선택하지 않습니다. 창·내용 이벤트로 재연결하고, 일시적인 화면 정보/창 연결 실패는 제한된 횟수만 재시도합니다. 대기 중 화면을 주기적으로 탐색하지 않습니다.
- `SessionService`는 사용자가 켠 세션에 `specialUse` 포그라운드 서비스를 제공합니다. `stopWithTask=false`와 `START_STICKY`로 최근 앱 화면과 수명을 분리합니다. 일시정지·접근성 해제 시 함께 종료하고, Android의 명시적인 앱 중지에 저항하는 알람이나 재시작 루프는 사용하지 않습니다. 추가 wake lock도 없습니다.
- 상태 화면은 저장된 권한 설정, 실제 접근성 연결, 실제 실행 유지 상태를 구분합니다.

## 스크롤

- `ScrollEngine`은 한 번에 한 실행만 허용하고 앱·창·대상 영역을 고정합니다. 최대 250개 노드를 탐색하며, 중첩 스크롤 컨테이너도 확인합니다. 가로 전용 목록·입력칸·범위 조절 위젯은 제외합니다.
- 빠른 모드에서도 먼저 첫 행 이동과 지원되는 Android 15 이상의 무한 스크롤 양 인자를 사용합니다. 이 동작의 실제 애니메이션은 대상 앱이 구현합니다. 앱에 따라 즉시 이동하거나 부드럽게 이동합니다.
- WebView/Chrome에서는 문서 순서의 첫 화면 밖 요소를 최대 24개 노드 안에서 찾아 `ACTION_SHOW_ON_SCREEN`으로 시작 부분을 드러냅니다. 클릭·포커스 변경·글 내용 읽기는 하지 않습니다. 이 요청은 대상 앱이 즉시 처리할 수 있으며, 이후 실제 위치를 확인해 남은 이동을 이어갑니다.
- 브라우저 도구 모음 변경 등으로 대상 정보가 잠시 무효화되면 60/120/240/480ms로 재시도합니다. 앱·창·클래스·식별자·영역 일치 검사는 유지하며, 다른 창에 이전 동작을 넘기지 않습니다.
- 빠른 전체 이동이 없으면 실제 위쪽 이동 지원 여부와 최근 스크롤 위치를 확인해 70ms 스와이프를 보냅니다. WebView가 위쪽 동작 목록을 누락하면 의미 스크롤로도 확인합니다. `ACTION_PAGE_UP`만 제공하는 화면도 지원합니다.
- 동작 후 최소 450ms를 두고, 마지막 이동 이벤트 이후 네이티브 이동은 160ms, 물리 스와이프는 80ms를 기다립니다. 스와이프에서는 이벤트의 실제 발생 시각을 사용해 전달 지연이 정지 시간으로 더해지는 것을 줄입니다. 이미 맨 위인 화면에는 물리적인 당김을 시작하지 않습니다. 끝에 가까우면 짧은 의미 스크롤로 마무리합니다.
- 빠른 모드는 한 번에 최대 12회의 제스처, 최대 120개 동작, 사용자가 지정한 4~20초 제한을 지킵니다. 선택적인 기본 호환 스와이프는 최대 4회입니다. 최근 위치 정보는 30초 이내이고 동일한 앱·창·대상일 때만 판단에 사용합니다.
- 다시 한 번 누르기·앱 전환·잠금·권한 해제·진행 없음·시간 제한은 추가 동작 전송을 중지합니다. 다른 앱이 이미 시작한 관성/애니메이션은 잠시 이어질 수 있습니다.

## 검증과 한계

실행 명령, APK 해시, 위치·시간 측정, 화면 비교 결과는 [VERIFICATION.md](VERIFICATION.md)에 기록합니다. 개인 기기가 아닌 명시한 에뮬레이터에서 별도 앱과 실제 Chrome을 사용합니다. Chrome용 문서는 이 PC의 로컬 서버와 해당 에뮬레이터의 ADB 터널에서만 제공합니다.

다른 앱의 내부 스크롤 애니메이션 곡선·속도·접근성 지원을 탑탭이 바꿀 수는 없습니다. 모든 앱이 아이폰과 똑같이 움직인다는 주장이나 제조사 정책을 무시하는 영구 실행 보장은 하지 않습니다. 삼성 실기기 검증과 스토어 심사는 별도입니다.

## 공식 근거

- [접근성 서비스](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [스크롤 동작](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction)
- [스크롤 양 인자](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT)
- [ListView의 첫 행 이동 구현](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/widget/ListView.java)
- [RecyclerView의 첫 행 이동 구현](https://github.com/androidx/androidx/blob/androidx-main/recyclerview/recyclerview/src/main/java/androidx/recyclerview/widget/LinearLayoutManager.java)
- [포그라운드 서비스 유형](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
- [사용자가 앱을 중지했을 때](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)
