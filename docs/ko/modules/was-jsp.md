# `was-jsp` 모듈 가이드

`was-jsp` 모듈은 고효율의 자체 통합된 서블릿 페이지 프레임워크인 JSP 엔진 컴파일러이자 자체 런타임으로써, 사용자가 작성한 Jakarta Server Pages(`.jsp`) 소스코드들을 즉각 런타임 도중에 동합하여 완전한 실구동 서블릿(Servlet) 클래스로 통역 및 재컴진행해 줍니다.

## 핵심 구성 요소

### 1. 구문 추출 및 파서 엔진 (`JspParser` / `JspDocument`)
원시 상태의 혼합 형태인 `.jsp` (기타 HTML 태그 요소와 자바 프로그래밍의 흔적인 스크립틀릿들이 버무려진 형태) 텍스트 파일 원문을 추상 구문 트리 형태 기반의 객체 트랙인 `JspDocument` 모델 스펙으로 쪼개 파싱(Parsing)해 내는 단계입니다.

### 2. JSP 네이티브 컴파일러 (`JspCompiler`)
앞서 파싱 완료된 `JspDocument` 트리를 삼켜 동적으로 새로운 서블릿 확장 호환 규격(javax.servlet.jsp.HttpJspPage 등)에 알맞는 매끄러운 자바 텍스트 원문 소스(`TranslatedSource`)로 번역(Translation)해 발급하는 가장 근간이 되는 번역가 요충지입니다.
- Jsp 생태계 표준인 내부의 재스퍼(Jasper) 환경, 혹은 ECJ (Eclipse Compiler for Java - 빠른 처리 효율 배후엔진) 과 맞붙어, 번역된 해당 `.java` 문자열 구조체 파일을 `was-config`가 지정한 유동적 디렉토리(`scratchDir` - `work/jsp`) 기반의 안전한 실행 가능한 바이트코드 `.class` 스펙으로 컴파일 적재시킵니다.

### 3. Jsp 실행 런타임 환경 (`JspServlet` & `VeloJspWriter`)
- **`JspServlet`**: `web.xml` 체계 안에서 범용 와일드카드 `*.jsp` 로 맵핑되는 Jsp 운영의 디폴트 표준 엔트리포인트(진입문) 서블릿입니다. 요청받은 Jsp 에 관한 `.class` 의 구조물이 오래되어 변질되었고 원문이 수정되었거나 또는 아예 스크래치 경로에 없다고 판단할 때 무결한 다운타임 없는 자연스러운 컴파일 재진행 사이클을 바로 트리거/격발합니다.
- **`VeloJspWriter`**: HTML 문서 전송을 감안해 특별히 고안/조립된 `JspWriter` 특수 판본입니다. `was-servlet-core`를 향한 메인 응답 프록시 하단으로 내용들을 밀어보내거나 닫기 이전에, 해당 스트림 아웃풋 버퍼들을 유실 없이 안전무결하게 가두어(Buffer) 관리합니다.
- **`SimpleElEvaluator`**: 현대 웹뷰 계층 표준 스펙인 JARKARTA Expression Language (예시: `${user.name}`)를 관통 처리하는 파서 엔진입니다. JSP 스코프인 `PageContext` 바운더리 내부 변수들과 긴밀하게 연동/맵핑됩니다.

### 4. 무중단 핫 리로딩 제어기 (`JspResourceManager` / `JspReloadManager`)
운영체제 물리 파일시스템에 안착해있는 스크립트 기반 `.jsp` 소스 원문들의 수정일자/디스크 타임스탬프 변동성을 철야로 마스킹하고 모니터링합니다.  `developmentMode = true` 환경 세팅일 시 갱신 변동이 일어났다면, `JspReloadManager`는 이전 세대 구형판의 `CompiledJsp` 레퍼런스 객체참조를 신명나게 폐기시키고, `JspServlet`으로 하여금 즉시 백엔드에서 일체의 중단 시간 없이 고군분투 자동 재해석 재컴파일 절차에 다시 오르도록 명령시그널을 타전합니다.
