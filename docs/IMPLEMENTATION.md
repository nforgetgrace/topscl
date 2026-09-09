# 구현

## 상태바와 수명

- `TopTapService`는 Android가 연결하는 접근성 서비스입니다. 상태바 전체 너비를 사용하며, 원터치만 지원합니다. `TriggerView`는 배경·리플·표시선을 그리지 않습니다. 동작 중에도 색이 바뀌지 않습니다.
- 아래로 드래그하면 시스템 알림창을 열고, 화면 오른쪽 1/3에서 드래그하면 빠른 설정을 엽니다. 길게 누르기·다중 터치·가로 이동은 상단 이동으로 처리하지 않습니다.
- 상태바가 숨겨진 전체 화면에서는 터치를 통과시키면서 창 여백 정보의 갱신을 계속 받습니다. 상태바가 복구되거나 활성 창이 바뀌면 영역을 다시 맞춥니다. 잠금·일시정지·제외 앱·권한 허용 창에서는 영역을 제거합니다.
- `ActiveWindow`는 일시적으로 비어 있는 활성 화면 캐시를 보완합니다. 시스템 창 너머의 앱을 선택하지 않습니다. 창·내용 이벤트로 재연결하고, 일시적인 화면 정보/창 연결 실패는 제한된 횟수만 재시도합니다. 대기 중 화면을 주기적으로 탐색하지 않습니다.
- `SessionService`는 사용자가 켠 세션에 `specialUse` 포그라운드 서비스를 제공합니다. `stopWithTask=false`와 `START_STICKY`로 최근 앱 화면과 수명을 분리합니다. 일시정지·접근성 해제 시 함께 종료하고, Android의 명시적인 앱 중지에 저항하는 알람이나 재시작 루프는 사용하지 않습니다. 추가 wake lock도 없습니다.
- 상태 화면은 저장된 권한 설정, 실제 접근성 연결, 실제 실행 유지 상태를 구분합니다.

## 스크롤

- `ScrollEngine`은 한 번에 한 실행만 허용하고 앱·창·대상 노드의 동일성을 확인합니다. 초기 탐색은 최대 250개 노드이며 최근에 스크롤한 동일 영역을 우선합니다. 웹 내부 컨테이너도 탐색합니다. 가로 전용 목록·입력칸·범위 조절 위젯은 제외합니다.
- 실행 중에는 대상 노드를 `refresh()`하고 최대 3개 자식의 좌표·행 번호를 확인합니다. 매 동작마다 전체 트리를 다시 탐색하지 않습니다. 노드 핸들은 실행 종료 시 해제하며, 화면 글이나 이미지 내용을 읽거나 로그로 남기지 않습니다.
- 대상이 재생성되어 핸들이 무효화되면 같은 앱·창 안에서 클래스·식별자·영역이 맞는 후보를 제한적으로 찾습니다. 후보가 하나일 때만 이어가고, 이전 노드의 위치 증거는 폐기합니다. 대상 부재는 60/120/240/480ms로 재시도합니다.
- 높이와 항목 번호 중 하나라도 양수이면 다른 값의 0만으로 맨 위라고 판단하지 않습니다. 첫 행의 잘림도 확인합니다. 이전 실행에서 기억한 0은 새 실행의 완료 증거로 사용하지 않습니다. 실제 스크롤 알림과 현재 위쪽 이동 동작의 지원 여부를 함께 확인합니다.
- 빠른 모드에서도 첫 행 이동과 지원되는 Android 15 이상의 무한 스크롤 양 인자를 우선합니다. 명령이 받아들여졌어도 맨 위 도착으로 간주하지 않으며 남은 이동을 확인합니다. 앱이 구현한 이동은 즉시 이동 또는 자체 애니메이션일 수 있습니다.
- WebView/Chrome에서는 문서 순서의 첫 화면 밖 요소를 최대 24개 노드 안에서 찾아 `ACTION_SHOW_ON_SCREEN`으로 시작 부분을 드러냅니다. 클릭·포커스 변경·글 내용 읽기는 하지 않습니다. `ACTION_PAGE_UP`만 제공하는 화면도 지원합니다.
- 전체 이동 동작이 없으면 실제 위쪽 이동 지원 여부와 위치 증거를 확인해 70ms 스와이프를 보냅니다. 최근 위치는 동일 노드의 1초 이내 양수 값만 초기 힌트로 사용합니다. 최근 영역 선택 힌트의 유효 기간은 30초입니다. 스크롤 알림이 없더라도 갱신된 자식 좌표와 행 번호가 바뀌면 진행으로 인정합니다.
- 물리 스와이프 후 최소 450ms와 마지막 확인된 이동 뒤 80ms를 둡니다. 네이티브 동작은 알림이 없을 때 450ms의 시작 여유를 두되, 움직임이 확인되면 64ms의 안정 구간과 실제 좌표 변화를 확인합니다. 네이티브 대기 중 좌표 검사 간격은 최대 40ms입니다. 초기 16ms 검사로 이미 일부 이동을 끝낸 동작 뒤의 불필요한 고정 대기를 줄입니다. 반복된 동일 위치 알림은 진행으로 세지 않습니다.
- 빠른 모드의 고정 12회 스와이프 제한은 제거했습니다. 최대 120개 동작, 사용자가 지정한 4~20초 제한과 3회 연속 진행 없음 중단 조건은 유지합니다. 기본 호환 모드의 선택적 스와이프는 최대 4회입니다.
- 다시 한 번 누르기·앱 전환·잠금·권한 해제·진행 없음·시간 제한은 추가 동작 전송을 중지합니다. 대상 앱이 이미 시작한 관성/애니메이션은 잠시 이어질 수 있습니다.

## 검증과 한계

실행 명령, APK 해시, 위치·시간 측정, 화면 비교 결과는 [VERIFICATION.md](VERIFICATION.md)에 기록합니다. 개인 기기가 아닌 명시한 에뮬레이터에서 별도 앱과 실제 Chrome을 사용합니다. Chrome용 문서는 이 PC의 로컬 서버와 해당 에뮬레이터의 ADB 터널에서만 제공합니다.

다른 앱의 내부 스크롤 애니메이션 곡선·속도·접근성 지원을 탑탭이 바꿀 수는 없습니다. 모든 앱이 아이폰과 똑같이 움직인다는 주장이나 제조사 정책을 무시하는 영구 실행 보장은 하지 않습니다. 삼성 실기기 검증과 스토어 심사는 별도입니다.

## 공식 근거

- [접근성 서비스](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [노드 최신 상태 확인](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#refresh())
- [스크롤 동작](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction)
- [스크롤 양 인자](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT)
- [ListView의 첫 행 이동 구현](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/widget/ListView.java)
- [RecyclerView의 첫 행 이동 구현](https://github.com/androidx/androidx/blob/androidx-main/recyclerview/recyclerview/src/main/java/androidx/recyclerview/widget/LinearLayoutManager.java)
- [포그라운드 서비스 유형](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
- [사용자가 앱을 중지했을 때](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)
