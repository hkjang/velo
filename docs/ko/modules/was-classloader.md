# `was-classloader` 모듈 가이드

`was-classloader` 모듈은 웹 애플리케이션 단위의 독립된 메모리 격리 환경(Isolation)을 보장하기 위한 가장 필수적인 생명유지 장치입니다. 기본 Java 애플리케이션에서는 모든 로드된 클래스 단들이 하나의 평면적인 클래스패스에 모여 순차 배열되지만, 서블릿이 혼재된 환경에선 의존성 충돌(Dependency collisions) 방지를 위해 각 배치된(Deployed) 애플리케이션마다 별개의 고유 클래스로더 체계를 갖추어야 합니다.

## 핵심 구성 요소

### `WebAppClassLoader`
표준 Java Servlet 환경만을 정밀하게 타겟팅하여 자체 고안된 전문 `ClassLoader` 파생 구현체입니다.

- **자식 우선 로딩 (Child-First Loading 방침 격리)**: Java 가 본래 가지고 있는 전통적인 부모 우선(Parent-First) 위임 방침을 전면 배제합니다. `WebAppClassLoader`는 서버 단의 부모 클래스로더로 처리를 위임하기 **이전**에, 배포된 앱의 `WEB-INF/classes` 와 `WEB-INF/lib/*.jar` 에 든 라이브러리 목록을 최우선 탐색합니다. 이로 인해 유저의 웹 애플리케이션 내부에서 특정 스프링(Spring) 버전이나 구아바(Guava) 패키지를 Velo WAS 내부 버전에 구애받지 않고 오롯이 가져갈 수 있는 강한 충돌 면역성을 제공합니다.
- **리소스 추출**: 거대한 웹 아카이브 패키징 안에 들은 일반 리소스 산출물들도 Stream 형식의 읽기전용 표준 URL 규격으로써 투명하게 조회 가능하도록 구현했습니다.
- **메모리 누수(Memory Leak) 완전 배제 처리 원리**: `was-admin` 콘솔 등으로부터 애플리케이션 배포 철회(`undeploy`)가 강제로 선언될 시, 해당 클래스로더 최상부에서부터의 위임 계층(Hierarchy) 자체를 끊어버려 폐기 처리합니다. 이는 메모리를 든든히 지키며, 애플리케이션이 남긴 흔적이나 낡은 Static 참조 인스턴스 모두를 Garbage Collector 이 가뿐히 거두어 갈 수단으로 전락시킵니다.
