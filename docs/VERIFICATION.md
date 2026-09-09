# 탑탭 검증 기록

검증일: 2026-09-09. 설치 파일은 `dist/TopTap-1.0.0-debug.apk`입니다. 개인 기기에서 설치해 사용해 볼 수 있는 디버그 서명 빌드이며, 스토어에 게시한 배포판은 아닙니다.

## 환경과 방법

- JDK 21, Android Gradle Plugin 9.2.1, Gradle 9.4.1, compile/target SDK 36, min SDK 26.
- 전용 ARM64 에뮬레이터: Android 15/API 35와 Android 16/API 36, 세로 1080×2400. 호스트 메모리 압박을 피하도록 최종 검사는 한 대씩 실행했습니다.
- 제품 앱 `kr.toptap.android`와 별도 프로세스의 합성 테스트 앱 `kr.toptap.fixture`를 사용합니다. fixture는 배포 APK에 포함되지 않습니다.
- 실제 상단 터치를 주입하고 fixture가 기록한 스크롤 위치로 결과를 판정합니다. 목록과 격자는 첫 항목 번호뿐 아니라 첫 행의 잘림이 0인지도 검사합니다. WebView는 실제 스와이프로 내린 다음 상단을 누릅니다.
- 자동화 준비 과정에서 테스트 에뮬레이터에만 동의 설정과 접근성 연결을 구성합니다. 제품의 최초 설명/동의 화면은 별도로 표시·확인했습니다. 실제 사용자 기기의 동의를 자동화하지 않습니다.

## 정적 검사와 빌드

`sh scripts/build.sh` 통과: 제품 및 fixture APK 빌드, 두 모듈의 Android lint, 터치 판정 13개 assertion.

제품 lint는 오류 0개, 버전 관련 경고 3개입니다(`OldTargetApi`, `AndroidGradlePluginVersion`, `GradleDependency`). SDK 36과 검증한 도구 버전을 고정했습니다. 테스트 전용 fixture에는 진단 문자열, 아이콘/백업 설정, 테스트 초기화 저장 방식 등의 경고 13개가 있으며 오류는 없습니다. Gradle은 향후 10.x 호환성 관련 deprecated API 안내를 출력합니다.

`debugRuntimeClasspath`는 `No dependencies`입니다. APK manifest의 `uses-permission` 선언은 없으며, 서비스 바인딩은 각각 Android의 접근성/빠른 설정 권한으로 보호됩니다. APK Signature Scheme v2 서명 검증을 통과했습니다. 정확한 파일 SHA-256은 [SHA256SUMS](../dist/SHA256SUMS)에 있습니다.

## 자동 실행 결과

동일한 최종 APK로 두 환경의 전체 검사가 모두 통과했습니다.

| 환경 | 결과 |
| --- | --- |
| Android 15 / API 35 | 19 / 19 통과 |
| Android 16 / API 36 | 19 / 19 통과 |
| Android 독립 터치 판정 | 13 / 13 assertion 통과 |

APK SHA-256: `b9f8dcbb0df639dfb2a960970b7cd6bef2080030359650bfee86bf5851e29693`.

개별 결과와 소요 시간은 아래 JSON에 기록했습니다.

- [Android 15 결과](e2e-results-api35.json)
- [Android 16 결과](e2e-results-api36.json)

각 환경의 전체 검사는 다음 19가지입니다.

| 범위 | 확인 내용 |
| --- | --- |
| 상단 이동 5가지 | 첫 행 직접 이동, granular 무한 이동 인자, 일반 목록, 사진 목록 형태의 격자, WebView |
| 잘못된 대상 방지 2가지 | 가로 전용 목록과 범위 조절 위젯을 변경하지 않음 |
| 호환 스와이프 2가지 | 기본 꺼짐 확인, 켰을 때 식별한 영역에서 동작 |
| 실행 제한 1가지 | 긴 웹 페이지에서 제한 시간 후 멈추고 다음 탭으로 이어서 이동 |
| 사용자 제어 5가지 | 일시정지, 앱 제외, 두 번 누르기, 상단 아래 드래그로 알림창 열기, 재활성화로 진행 중 동작 중지 |
| 복구와 경계 4가지 | 다른 창으로 이동 시 중지, 프로세스 강제 제거 후 OS 재연결과 실제 스크롤, 화면 꺼짐/해제, 접근성 권한 철회 |

프로세스 복구 검사는 기존 PID를 종료하고 새 PID의 표시 가능한 오버레이가 생성된 뒤 스크롤까지 확인합니다. 이는 사용자가 Android 설정에서 **강제 종료**한 상태와 다릅니다. 강제 종료·권한 철회를 우회한다고 주장하지 않습니다.

```sh
sh scripts/build.sh
python3 scripts/e2e.py --serial emulator-5580 --adb /your/android/sdk/platform-tools/adb
python3 scripts/e2e.py --serial emulator-5582 --adb /your/android/sdk/platform-tools/adb
```

이 테스트 스크립트는 명시한 에뮬레이터의 TopTap 테스트 설정을 변경하고, 테스트 종료 시 기존 접근성 서비스 목록을 복원합니다. 개인 기기를 지정하면 실행하지 않습니다.

## 화면 확인

실제 네이티브 UI 7개 상태를 캡처했습니다. 기본 홈/설정/도움말, 글자 크기 2배의 홈/설정, 가로 설정/도움말입니다. 세 탭의 탐색 가능 여부를 확인했고 가로 화면은 2400×1080입니다. 긴 설정은 스크롤 가능하며, 큰 글자의 위치 선택 버튼은 세로로 배치합니다.

- [홈](screenshots/home.png)
- [설정](screenshots/settings.png)
- [도움말](screenshots/help.png)
- [큰 글자](screenshots/settings-large-text.png)
- [가로 화면](screenshots/settings-landscape.png)

디자인 기준은 [DESIGN.md](../DESIGN.md)입니다. 외부 앱의 그림이나 로고를 복제하지 않고 벡터 아이콘과 Canvas 그림을 작성했습니다.

## 미검증 범위와 제한

삼성 실기기, One UI, 삼성 인터넷·갤러리의 실제 화면, 밤새 대기/절전, 폴더블 전환, 다중 디스플레이, 재부팅 후 제조사 정책은 아직 검증하지 않았습니다. Android 8~14는 최소 API 설정과 정적 검사 범위이며 실행 검증은 하지 않았습니다. 빠른 설정 타일의 실기기 사용과 TalkBack 동시 사용도 추가 확인 대상입니다. [실기기 확인표](QA-CHECKLIST.md)에 남겨 두었습니다.

모든 앱이 접근성 스크롤 동작을 제공하지는 않습니다. 아주 긴 페이지는 기본 12초(최대 20초)에 도달하면 멈춰 다음 탭이 필요할 수 있습니다. OS·제조사 정책을 무시하는 영구 실행이나 모든 앱에서의 한 번 탭 완료를 보장하지 않습니다.
