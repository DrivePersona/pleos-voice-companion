# Android 패키지 규칙

각 Gradle 모듈은 독립된 기능 경계이며, 모듈 안에서는 역할로 패키지를 나눈다. 새 코드는 파일 크기나 화면 이름이 아니라 아래 책임에 따라 배치한다.

```text
app/src/main/java/com/example/voicecompanion/
  app/           Application·런타임 조립과 앱 수명
  app/model/     화면에 전달하는 읽기 전용 UI 상태 모델
  domain/        차량 반응 규칙과 외부 포트
  domain/model/  순수 도메인 값과 기록 모델
  data/audio/    Android·Pleos 음성 및 오디오 포커스 어댑터
  data/vehicle/  차량 SDK·주행 규제 어댑터와 VehicleInputRepository
  ui/            Activity와 Compose 화면
  ui/theme/      앱 고유 색상·타이포그래피·형상 토큰과 CompanionTheme
  ui/component/  토큰을 적용한 재사용 Compose 컴포넌트
  ui/event/      터치·입력 이벤트와 일회성 UI 효과
  ui/viewmodel/  StateFlow UI 상태와 터치 이벤트 처리
  ui/overlay/    WindowManager 기반 캐릭터 오버레이 UI

voicepack/       data/, domain/, model/, util/
characterpack/   data/, domain/, model/, ui/, util/ — GLB 3D와 검증된 PNG 스프라이트 2D 표현을 같은 선택 계약으로 제공
```

- `model`과 `util`은 화면·Activity에 의존하지 않는다.
- `domain`은 정책·계약을 갖고 Android·Pleos 구현을 직접 참조하지 않는다.
- `data`는 파일, SDK, Android 시스템처럼 외부 입력을 구현한다.
- `ui`는 상태를 표시하고 `CompanionTouch`를 전달한다. 차량 규칙이나 팩 검증을 직접 구현하지 않는다.
- `ui/theme`과 `ui/component`는 화면별 하드코딩 대신 공통 시각·상호작용 규칙을 제공한다. 기준은 `DESIGN_SYSTEM.md`에 둔다.
- `ui/viewmodel`은 `StateFlow<CompanionUiState>`를 제공하고, 터치 이벤트를 `CompanionUiRuntime` 계약으로 전달한다.
- `data/vehicle`의 `VehicleInputRepository`는 Pleos·시뮬레이터·다른 차량 SDK 입력을 같은 `VehicleInput` 모델로 바꿔 Runtime에 전달한다.
- `app`만 위 요소를 조립한다. `MainActivity`는 생명주기와 화면 진입·시스템 효과만 맡고 화면 구성은 `CompanionScreen`에 둔다.

테스트도 대상 패키지를 따라 배치한다. 실제 디버그 팩을 읽는 교차 검사는 `integration/`에 둔다.

캐릭터 팩은 `format: "glb"` 또는 `format: "sprite-sheet"`을 선언한다. 두 형식 모두 `files`의 크기·SHA-256과 이벤트별 `animations`를 검증한다. 스프라이트 팩은 PNG 실측 크기와 격자/프레임 시간도 추가로 확인한다. 권리 확인이 끝나지 않은 참조 기반 캐릭터는 `provenance.character: "reference-derived"`, `commercialUse: false`로 선언하고 `app/src/debug/assets/characterpacks/`에만 둔다. release 에셋과 제품 배포에는 넣지 않는다.
