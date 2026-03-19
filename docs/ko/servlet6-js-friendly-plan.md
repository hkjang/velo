# Servlet 6 + JS 친화 WAS 개발 계획

이 문서는 현재 `velo-was` 저장소 상태를 기준으로, Tomcat 10.1 / Jetty 12 계열과 비교 가능한 Servlet 6 기반 WAS를 제품형으로 끌어올리기 위한 개발 목록과 실행 우선순위를 정리한다.

## 목표

- Jakarta Servlet 6.x 호환성 강화
- WAR 배포/운영/관측성 고도화
- 브라우저 JavaScript 렌더링 친화 기능 정교화
- 역프록시, HTTP/2, WebSocket, 정적 자산 전송 품질 개선

## 현재 상태 요약

이미 구현된 축:

- Servlet 컨테이너: `Filter`, `Listener`, `RequestDispatcher`, `AsyncContext`, 세션 TTL, multipart
- 배포: exploded WAR, `web.xml` 파싱, 핫 디플로이, 클래스 로더 격리
- 프로토콜: HTTP/1.1, HTTP/2, TLS ALPN, WebSocket
- 운영: health/info/metrics, 구조화 로그, graceful shutdown
- 프록시 친화: `Forwarded`, `X-Forwarded-*` 기반 외부 URL 복원

이번 작업에서 추가한 축:

- WAR 앱 기본 정적 리소스 서블릿 자동 등록
- `.js`, `.mjs`, `.map`, `.wasm`, 폰트 등 브라우저 친화 MIME 처리
- `ETag`, `Last-Modified`, 조건부 GET(304)
- fingerprint 자산 `immutable` 캐시 정책
- `.br` / `.gz` 사전 압축 자산 우선 전달
- SPA fallback, source map on/off, `WEB-INF` / `META-INF` 차단
- welcome-file 이 실제 리소스 존재 여부를 기준으로 동작하도록 보강

## 요구사항 백로그

### P0. 표준 호환 핵심

- `@WebServlet`, `@WebFilter`, `@WebListener` annotation 스캔
- `security-constraint`, `login-config`, auth-method 최소 구현
- 세션 쿠키 정책: `Secure`, `SameSite`, 재발급 정책 설정화
- `sendRedirect()` 상대/절대 URL 동작과 프록시 aware redirect 정교화
- multipart 제한/임시 파일 정책 고도화

### P1. JS 렌더링 친화 핵심

- 정적 자산 range request, 대용량 다운로드 처리
- CSP 정책 주입과 report endpoint
- 정적 자산 캐시 정책 서버 설정화
- CORS 정책 엔진 정식화
- JS/CSS/모듈 MIME 오류 및 404 원인 로그 강화

### P1. 운영 필수

- request id / trace id 전파
- Prometheus 포맷 `/metrics`
- slow request log
- readiness / liveness 분리
- 배포/세션/커넥션 상태를 CLI/Web Admin 실데이터와 연결

### P2. 제품화 고도화

- HTTP/3 검토 및 아키텍처 분리
- classloader leak 탐지
- 분산 세션 저장소 어댑터
- 설정 핫 리로드 정책 세분화
- Servlet 계약 회귀 테스트 세트 확장

## 실행 순서

### 1단계

- annotation 기반 배포 모델 완성
- 보안 제약 파싱 및 최소 enforcement
- 세션 쿠키 속성 설정화

완료 기준:

- `web.xml` 없이도 `@WebServlet` 샘플 WAR 배포 성공
- BASIC/FORM 연계 지점을 테스트로 확인
- `JSESSIONID`에 `Secure` / `SameSite` 정책 적용 가능

### 2단계

- 정적 자산 range request
- CSP / CORS / source map 운영 옵션 정식화
- 정적 자산 오류 관측 로그 강화

완료 기준:

- JS 번들 다운로드 재개와 대용량 파일 range 테스트 통과
- CSP 위반 수집 경로와 설정 문서 제공
- MIME/CORS/CSP 실패 원인이 로그에서 식별 가능

### 3단계

- request id, Prometheus, slow request log
- Web Admin / CLI 실데이터 연동 강화
- readiness / liveness 추가

완료 기준:

- 외부 수집기에서 `/metrics`를 바로 읽을 수 있음
- 요청 단위 상관관계가 access/app/error 로그에 연결됨
- 운영자가 배포/세션/스레드 상태를 UI와 CLI에서 동일하게 확인 가능

## 이번 작업 산출물

- `was-servlet-core`
  - `StaticResourceServlet` 추가
  - welcome-file 리소스 존재 확인 보강
- `was-deploy`
  - WAR 앱 기본 정적 서블릿 자동 등록
- 테스트
  - 정적 자산 통합 테스트 추가
  - WAR 배포 단위 테스트 보강

## 검증 포인트

- `/site/` -> welcome-file `index.html`
- `/site/assets/app.mjs` -> `text/javascript`
- `/site/assets/app.<hash>.js` + `Accept-Encoding: br` -> `Content-Encoding: br`
- `If-None-Match` -> `304`
- source map 비활성화 시 `.map` -> `404`
- `/site/dashboard/overview` + `Accept: text/html` -> SPA fallback
