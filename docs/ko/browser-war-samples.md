# Browser WAR 샘플 테스트

`velo-was` 의 정적 리소스 서빙, MIME, ES Module, reverse proxy 친화 기본 설정이
브라우저 프레임워크 샘플과 함께 정상 동작하는지 확인하기 위한 WAR 샘플 모음이다.

## 포함된 샘플

| 기술 | WAR 파일 | 테스트 링크 | 기대 결과 |
|---|---|---|---|
| JavaScript | `deploy/sample-vanilla-js.war` | `http://127.0.0.1:8080/sample-vanilla-js/` | 상태 뱃지가 `READY` 이고 서비스 카드 5개가 렌더링된다. |
| TypeScript | `deploy/sample-typescript.war` | `http://127.0.0.1:8080/sample-typescript/` | 상태 뱃지가 `READY` 이고 타입스크립트 번들이 정상 실행된다. |
| jQuery | `deploy/sample-jquery.war` | `http://127.0.0.1:8080/sample-jquery/` | 상태 뱃지가 `READY` 이고 jQuery DOM 업데이트가 보인다. |
| ReactJS | `deploy/sample-reactjs.war` | `http://127.0.0.1:8080/sample-reactjs/` | 상태 뱃지가 `READY` 이고 React 상태 기반 카드가 렌더링된다. |
| VueJS | `deploy/sample-vuejs.war` | `http://127.0.0.1:8080/sample-vuejs/` | 상태 뱃지가 `READY` 이고 Vue reactive 리스트가 렌더링된다. |
| Angular | `deploy/sample-angular.war` | `http://127.0.0.1:8080/sample-angular/` | 상태 뱃지가 `READY` 이고 Angular standalone 컴포넌트가 렌더링된다. |

## 소스 위치

- 샘플 소스: `samples/browser-wars/`
- 패키징 스크립트: `scripts/build-browser-sample-wars.ps1`
- 공통 데이터: `samples/browser-wars/common/dashboard.json`

## 빌드와 배포

```powershell
.\scripts\use-local-toolchain.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\build-browser-sample-wars.ps1
```

위 스크립트는 각 샘플을 개별 WAR로 묶어 `deploy/` 에 배치한다.

## 실행

```powershell
java -jar .\was-bootstrap\target\was-bootstrap-0.5.8-jar-with-dependencies.jar .\conf\server.yaml
```

기본 설정 기준으로 `deploy/` 를 hot deploy 하므로, 서버가 떠 있으면 WAR를 덮어쓴 뒤
자동 재배포된다.

## 검증 포인트

- 각 페이지가 `dashboard.json` 을 fetch 한다.
- 상태 뱃지가 `READY` 로 바뀐다.
- 메트릭 카드 4개와 서비스 카드 5개가 보인다.
- 브라우저 콘솔 에러가 없다.

## 참고

- jQuery, React, Vue, Angular 런타임은 CDN 자산을 사용한다.
- Angular 샘플은 zoneless bootstrap 구성을 사용한다.
