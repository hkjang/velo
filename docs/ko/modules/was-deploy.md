# `was-deploy` 모듈 가이드

`was-deploy` 모듈은 아무 가공이 안 된 상태인 거대한 정적 웹 애플리케이션 패키징 압축파일(`.war`)과, `was-servlet-core`에 의해 실제 런타임 상 구동되는 `ServletApplication` 환경을 이어주는 링커(Linker)이자 다리 역할을 합니다.

## 핵심 구성 요소

### 1. `WarExtractor`
`.war` 확장자의 바이너리로 압축된 파일을 물리적으로 쪼개어 해제(Unpacking)하는 임무를 맡습니다.
- OS 전용 임시 디렉토리 혹은 `server.yaml` 에 설정된 가동할 스크래치(Scratch) 루트 경로로 해당 압축을 매끈하게 해제시킵니다.
- 이후 Classloader 단계와 문맥 파싱에 쓰이게 될 핵심 기조인 `WEB-INF` 표준 구조 트리 뼈대를 확보해 둡니다.

### 2. `WebXmlParser` 및 `WebXmlDescriptor`
`WEB-INF/web.xml` 선언 구성요소를 파헤치고 읽어들이는 가장 중심적인 파싱 처리 엔진입니다.
- **파서 (Parser)**: 일반적인 SAX/DOM 표준 XML 핸들링 엔진을 이용하여, 낡은 체계의 XML 설정 원문 요소들을 Java 진영 안에 살판 돋게 인메모리 파싱 시켜 담아냅니다 (`WebXmlDescriptor`).
- **디스크립터 (Descriptor)**: 파서가 뽑아낸 문자열 조각들을 강력한 타입 기반 객체계(Strongly-typed) 형태로 재생성합니다. 이곳엔 서블릿 경로의 라우팅 매핑 규칙, 필터 체인 매핑 단계, 구동 리스너(Listeners), 로드온스타트업(Static Initializers) 환경, 임시 컨텐스트 파라미터나 초기 환영 페이지(Welcome Files) 가 담기게 됩니다.

### 3. `WarDeployer`
설치 단계에서 가장 중점이 되는 디플로이제네레이션(Deployment) 오케스트레이션 수행계입니다.
1. `WarExtractor` 를 작동하여 바이너리를 풀고 환경을 스테이징 진열시킵니다.
2. `WebXmlParser` 에 명령해 구동에 필요한 내부 라우팅 원칙을 온전히 습득합니다.
3. `was-classloader` 진영에서 `WebAppClassLoader`를 강제 차용, 애플리케이션 스펙의 구동용 바이트코드 전체를 컴파일 적재시킵니다.
4. 조립이 모두 끝난 종단 `SimpleServletApplication` 를 건조하여 최종 생명 통제자인 `ServletContainer` 측에 양도함으로써 외부 HTTP 트래픽 방어 임무에 정식 투입하게 만듭니다.
