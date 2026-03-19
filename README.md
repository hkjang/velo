# velo-was

Netty 湲곕컲 ?뷀꽣?꾨씪?댁쫰 WAS ?뚯슫?곗씠??

- **Tomcat湲??쒕툝由??명솚??* - Jakarta Servlet 6.1 API 湲곕컲 而⑦뀒?대꼫
- **Jetty湲??꾨줈?좎퐳/I/O ?깅뒫** - Netty ?ㅼ씠?곕툕 ?꾩넚 (epoll/kqueue/nio)
- **JEUS湲??댁쁺/愿由ъ꽦** - YAML ?ㅼ젙, 紐⑤뱢 遺꾨━, ?뺤옣 媛?ν븳 援ъ“

## 湲곗닠 ?ㅽ깮

| ??ぉ | 踰꾩쟾 |
|---|---|
| Java | 21 |
| Netty | 4.1.131.Final |
| Jakarta Servlet API | 6.1.0 |
| SnakeYAML | 2.5 |
| SLF4J | 2.0.17 |
| JUnit 5 | 5.12.1 |
| Maven | 3.9.13 |

## 紐⑤뱢 援ъ“

```
velo-was (13 modules)
?쒋?? was-config              ?쒕쾭 ?ㅼ젙 紐⑤뜽 (?쒖닔 POJO, ?몃? ?섏〈???놁쓬)
?쒋?? was-observability       援ъ“?붾맂 濡쒓퉭 (?≪꽭???먮윭/媛먯궗)
?쒋?? was-protocol-http       HTTP ?꾨줈?좎퐳 異붿긽??+ WebSocket
?쒋?? was-transport-netty     Netty ?쒕쾭 遺?몄뒪?몃옪 (HTTP/2, TLS ALPN)
?쒋?? was-servlet-core        ?쒕툝由?而⑦뀒?대꼫 (AsyncContext, ?몄뀡 TTL)
?쒋?? was-classloader         ???좏뵆由ъ??댁뀡 ?대옒?ㅻ줈??寃⑸━
?쒋?? was-deploy              WAR 諛고룷 ?뚯씠?꾨씪??
?쒋?? was-jndi                JNDI ?ㅼ씠諛?+ DataSource 而ㅻ꽖???
?쒋?? was-admin               愿由?CLI (jeusadmin ?명솚, 73媛?紐낅졊??
?쒋?? was-jsp                 JSP 吏??(?ㅽ뿕??
?쒋?? was-tcp-listener        TCP 由ъ뒪??
?붴?? was-bootstrap           湲곕룞 吏꾩엯??+ ?듯빀 ?뚯뒪??
```

媛?紐⑤뱢???곸꽭 臾몄꽌??紐⑤뱢 ?붾젆?좊━??`README.md`瑜?李멸퀬?쒕떎. ?꾪궎?띿쿂 ?곸꽭??[`docs/ko/architecture.md`](docs/ko/architecture.md)瑜?李멸퀬?쒕떎.

## 鍮뚮뱶

```bash
# 濡쒖뺄 ?댁껜???ㅼ젙 (Windows PowerShell)
.\scripts\use-local-toolchain.ps1

# ?꾩껜 鍮뚮뱶
mvn clean package

# ?뚯뒪?몃쭔 ?ㅽ뻾
mvn test
```

## ?ㅽ뻾

```bash
java -jar was-bootstrap/target/was-bootstrap-0.5.7-jar-with-dependencies.jar [config-path]
```

- `config-path` 誘몄?????`conf/server.yaml` ?ъ슜
- 硫붿씤 ?대옒?? `io.velo.was.bootstrap.VeloWasApplication`

## ?ㅼ젙

?ㅼ젙 ?뚯씪: [`conf/server.yaml`](conf/server.yaml)

```yaml
server:
  name: velo-was
  nodeId: node-1
  gracefulShutdownMillis: 30000

  listener:
    host: 0.0.0.0
    port: 8080
    soBacklog: 2048
    reuseAddress: true
    tcpNoDelay: true
    keepAlive: true
    maxContentLength: 10485760      # 10 MB
    idleTimeoutSeconds: 60          # ?좏쑕 ?곌껐 ??꾩븘??
    maxHeaderSize: 8192             # HTTP ?ㅻ뜑 理쒕? ?ш린
    maxInitialLineLength: 4096      # HTTP ?붿껌 ?쇱씤 理쒕? 湲몄씠

  threading:
    bossThreads: 1
    workerThreads: 0                # 0 = Netty 湲곕낯媛?
    businessThreads: 32

  compression:
    enabled: false                  # gzip ?뺤텞 ?쒖꽦??
    minResponseSizeBytes: 1024      # 理쒖냼 ?뺤텞 ????ш린
    compressionLevel: 6             # zlib ?뺤텞 ?덈꺼 (1-9)

  session:
    timeoutSeconds: 1800            # ?몄뀡 ??꾩븘??(30遺?
    purgeIntervalSeconds: 60        # 留뚮즺 ?몄뀡 ?뺣━ 二쇨린

  deploy:
    directory: deploy               # WAR 諛고룷 ?붾젆?좊━
    hotDeploy: false                # ???뷀뵆濡쒖씠 ?쒖꽦??
    scanIntervalSeconds: 5          # ?붾젆?좊━ 媛먯떆 ?붾컮?댁뒪

  tls:
    enabled: false
    mode: PEM                       # PEM ?먮뒗 PKCS12
    certChainFile: ""
    privateKeyFile: ""
    protocols: [TLSv1.3, TLSv1.2]
    reloadIntervalSeconds: 30
```

## API ?붾뱶?ъ씤??

| 寃쎈줈 | 硫붿꽌??| ?ㅻ챸 |
|---|---|---|
| `/health` | GET, HEAD | ?쒕쾭 ?곹깭 ?뺤씤 |
| `/metrics` | GET | ?쒕쾭 硫뷀듃由?(?붿껌 ?? ?쒖꽦 ?곌껐, ?묐떟 ?쒓컙) |
| `/info` | GET, HEAD | ?쒕쾭 ?뺣낫 議고쉶 |
| `/app/hello` | GET | ?섑뵆 ?쒕툝由?(?몄뀡 湲곕컲 諛⑸Ц ?잛닔 異붿쟻) |

?묐떟 ?덉떆:

```bash
# Health check
curl http://localhost:8080/health
# {"status":"UP","name":"velo-was","nodeId":"node-1"}

# Metrics
curl http://localhost:8080/metrics
# {"totalRequests":150,"activeRequests":2,"activeConnections":5,"averageResponseTimeMs":12.34,"status":{"1xx":0,"2xx":140,"3xx":3,"4xx":5,"5xx":2}}

# Server info
curl http://localhost:8080/info
# {"product":"velo-was","phase":"servlet-foundation","transport":"netty","servletCompatibility":"minimal"}

# Sample servlet
curl http://localhost:8080/app/hello
# {"message":"Hello from Velo Servlet","contextPath":"/app","servletPath":"/hello","visits":1,...}
```

## ?꾩옱 援ы쁽 踰붿쐞

- Maven 硫?곕え???꾨줈?앺듃 ?덉씠?꾩썐 (13媛?紐⑤뱢, 170+ ?뚯뒪??
- YAML 湲곕컲 ?쒕쾭 ?ㅼ젙 濡쒕뵫 諛?寃利?
- Netty HTTP/1.1 + **HTTP/2** ?고???(graceful shutdown)
- ?ㅼ씠?곕툕 ?꾩넚 ?먮룞 ?좏깮 (epoll/kqueue/nio)
- TLS 遺?몄뒪?몃옪 諛??몄쬆????由щ줈??(ALPN h2/h1.1 ?묒긽 ?ы븿)
- **HTTP/2**: TLS ALPN + ?대━?댄뀓?ㅽ듃 h2c + ?ㅽ듃由?硫?고뵆?됱떛
- **WebSocket**: 寃쎈줈 湲곕컲 ?몃뱾???덉??ㅽ듃由? ?띿뒪??諛붿씠?덈━ ?꾨젅??
- **Gzip ?뺤텞**: `HttpContentCompressor` 湲곕컲 ?묐떟 ?뺤텞 (?ㅼ젙 媛??
- **?좏쑕 ?곌껐 ??꾩븘??*: `IdleStateHandler` 湲곕컲 ?먮룞 ?곌껐 醫낅즺
- **HTTP 肄붾뜳 ?쒗븳**: 理쒕? ?ㅻ뜑 ?ш린, 理쒕? ?붿껌 ?쇱씤 湲몄씠 ?ㅼ젙
- ?쒕툝由?而⑦뀒?대꼫
  - `HttpServlet` dispatch (context path + servlet path longest-match)
  - `Filter` chain ?ㅽ뻾 (DispatcherType 湲곕컲 留ㅼ묶)
  - `ServletContextListener`, `ServletContextAttributeListener`, `ServletRequestListener` lifecycle
  - `HttpSessionListener`, `HttpSessionAttributeListener`, `HttpSessionIdListener`
  - `RequestDispatcher` forward/include (?곷? 寃쎈줈 諛?`..` ?댁꽍 ?ы븿)
  - `error-page` 留ㅽ븨 (`error-code`, `exception-type`) + `DispatcherType.ERROR`
  - In-memory `JSESSIONID` 荑좏궎 ?몄뀡 + **TTL 留뚮즺 ?ㅼ?以꾨윭** (??꾩븘???ㅼ젙 媛??
  - `changeSessionId()` 湲곕컲 session fixation ?꾪솕
  - **AsyncContext**: dispatch, complete, timeout, listener
  - **Multipart**: `multipart/form-data` ?뚯떛, `Part` API 吏??
  - ?숈쟻 ?꾨줉??湲곕컲 `HttpServletRequest`/`HttpServletResponse`/`ServletContext`/`HttpSession`
  - TLS 媛먯? (`isSecure()`, `getScheme()`)
- **WAR 諛고룷**: web.xml ?뚯떛, ?대옒?ㅻ줈??寃⑸━ (parent-first/child-first)
- **諛고룷 ?붾젆?좊━**: `deploy/` ?붾젆?좊━ 湲곕컲 ?먮룞 WAR 諛고룷
- **???뷀뵆濡쒖씠**: `WatchService` 湲곕컲 WAR ?뚯씪 蹂寃?媛먯? 諛??먮룞 ?щ같??
- **硫뷀듃由??섏쭛**: `LongAdder` 湲곕컲 ?붿껌 ?? ?쒖꽦 ?곌껐, ?묐떟 ?쒓컙, HTTP ?곹깭 肄붾뱶 遺꾪룷
- **援ъ“?붾맂 濡쒓퉭**: ?≪꽭???먮윭/媛먯궗 濡쒓렇 (JSON ?뺤떇)
- **JNDI / DataSource**: In-Memory ?ㅼ씠諛?而⑦뀓?ㅽ듃, JDBC 而ㅻ꽖???
- **Admin CLI**: 14媛?移댄뀒怨좊━ 73媛?紐낅졊?? JLine ?명꽣?숉떚釉??? JMX ?듯빀
- ?댁옣 health/info/metrics ?붾뱶?ъ씤??
- 蹂댁븞 ?ㅻ뜑 湲곕낯 ?ы븿 (nosniff, DENY, no-store, no-referrer)

## 臾몄꽌

| 臾몄꽌 | ?ㅻ챸 |
|---|---|
| [?꾪궎?띿쿂 媛쒖슂](docs/ko/architecture.md) | 紐⑤뱢 援ъ“, ?붿껌 泥섎━ ?먮쫫, ?ㅺ퀎 寃곗젙 |
| [?꾪궎?띿쿂 ?곸꽭](docs/ko/architecture-detail.md) | ?대? ?숈옉 ?ъ링 遺꾩꽍 |
| [AsyncContext](docs/ko/async-context.md) | 鍮꾨룞湲??쒕툝由?吏??|
| [WAR 諛고룷](docs/ko/war-deployment.md) | WAR 諛고룷 + ?대옒?ㅻ줈??寃⑸━ + ???뷀뵆濡쒖씠 |
| [援ъ“?붾맂 濡쒓퉭](docs/ko/structured-logging.md) | ?≪꽭???먮윭/媛먯궗 濡쒓렇 + 硫뷀듃由?(JSON) |
| [HTTP/2 + WebSocket](docs/ko/http2-websocket.md) | ALPN, h2c, WebSocket ?낃렇?덉씠??|
| [?몄뀡 愿由?(docs/ko/session-management.md) | TTL 留뚮즺 + ?댁쨷 ?쒓굅 ?꾨왂 + ?ㅼ젙 ?곕룞 |
| [?대윭?ㅽ꽣 ?몄뀡 媛?대뱶](docs/ko/cluster-session-guide.md) | ??μ냼 SPI, sticky session, 鍮꾨룞湲?蹂듭젣, TTL/異⑸룎 ?쇨???|
| [JNDI / DataSource](docs/ko/jndi-datasource.md) | JNDI ?ㅼ씠諛?+ 而ㅻ꽖??? |
| [Admin CLI](docs/ko/admin-cli.md) | 73媛?愿由?紐낅졊???덊띁?곗뒪 |
| [?쇱씠?꾩궗?댄겢](docs/ko/lifecycle.md) | ?쒕쾭/???앸챸二쇨린 |
| [?쒗뭹??濡쒕뱶留?(docs/ko/roadmap.md) | ?곗꽑?쒖쐞 ?ъ젙??+ 6二??ㅽ뻾 怨꾪쉷 |

## ?ν썑 怨꾪쉷

?곸꽭 ?곗꽑?쒖쐞? ?④퀎蹂??ㅽ뻾 怨꾪쉷? [`docs/ko/roadmap.md`](docs/ko/roadmap.md)瑜?李멸퀬?쒕떎.

1. **?쒕툝由??ㅽ럺 ?꾩꽦??媛뺥솕**: `web.xml`, ?먮윭 ?붿뒪?⑥튂, 由ъ뒪?? ?몄뀡 蹂댁븞 蹂닿컯
2. **?댁쁺 湲곕뒫 ?ㅻ뜲?댄꽣??*: CLI/Web Admin???ㅼ젣 ?고????곹깭瑜?吏곸젒 ?몄텧
3. **?뚯뒪???먯궛 ?뺣?**: ?щ같?? ?먮┛ ?대씪?댁뼵?? HTTP/2, WebSocket ?뚭? ?뚯뒪??
4. **愿痢≪꽦 ?쒖???*: Prometheus, request id, slow request log, ?깅퀎 硫뷀듃由??쒓퉭

