# 鍮뚮뱶 / 湲곕룞 / 醫낅즺 ?ㅽ겕由쏀듃 媛?대뱶

Velo WAS??Linux/macOS, Windows CMD, Windows PowerShell 媛??섍꼍??留욌뒗 鍮뚮뱶쨌湲곕룞쨌醫낅즺 ?ㅽ겕由쏀듃瑜??쒓났?쒕떎.
紐⑤뱺 ?ㅽ겕由쏀듃??`bin/` ?붾젆?좊━???꾩튂?쒕떎.

## ?붾젆?좊━ 援ъ“

```
bin/
?쒋?? build.sh          # Linux / macOS 鍮뚮뱶
?쒋?? build.bat         # Windows CMD 鍮뚮뱶
?쒋?? build.ps1         # Windows PowerShell 鍮뚮뱶
?쒋?? start.sh          # Linux / macOS 湲곕룞
?쒋?? start.bat         # Windows CMD 湲곕룞
?쒋?? start.ps1         # Windows PowerShell 湲곕룞
?쒋?? stop.sh           # Linux / macOS 醫낅즺
?쒋?? stop.bat          # Windows CMD 醫낅즺
?붴?? stop.ps1          # Windows PowerShell 醫낅즺
```

## ?ъ쟾 ?붽뎄?ы빆

### ?먮룞 媛먯? ?꾧뎄泥댁씤

?ㅽ겕由쏀듃???꾨줈?앺듃 ??濡쒖뺄 ?꾧뎄泥댁씤??癒쇱? 李얘퀬, ?놁쑝硫??쒖뒪???ㅼ튂瑜??ъ슜?쒕떎.

| ?꾧뎄 | 濡쒖뺄 寃쎈줈 | ?쒖뒪???대갚 |
|------|-----------|-------------|
| JDK 21 | `.tools/jdk/jdk-21.0.10+7` | `JAVA_HOME` ?섍꼍蹂??|
| Maven 3.9 | `.tools/maven/apache-maven-3.9.13` | `mvn` 紐낅졊??(PATH) |

> **以묒슂**: Velo WAS??Java 21 switch expression ?깆쓽 臾몃쾿???ъ슜?섎?濡?諛섎뱶??JDK 21 ?댁긽???꾩슂?섎떎.

---

## 鍮뚮뱶 ?ㅽ겕由쏀듃

### 湲곕낯 ?ъ슜踰?

```bash
# Linux / macOS
./bin/build.sh

# Windows CMD
bin\build.bat

# Windows PowerShell
.\bin\build.ps1
```

### ?듭뀡

| ?듭뀡 | sh | bat | ps1 | ?ㅻ챸 |
|------|-----|-----|-----|------|
| Clean | `-c`, `--clean` | `-c`, `--clean` | `-Clean` | 鍮뚮뱶 ??clean ?섑뻾 |
| Test | `-t`, `--test` | `-t`, `--test` | `-Test` | ?뚯뒪???ㅽ뻾 |
| Skip Tests | `-s`, `--skip-tests` | `-s`, `--skip-tests` | (湲곕낯媛? | ?뚯뒪??嫄대꼫?곌린 (湲곕낯) |
| Package | `-p`, `--package` | `-p`, `--package` | `-Package` | Fat JAR ?⑦궎吏?|
| Quiet | `-q`, `--quiet` | `-q`, `--quiet` | `-Quiet` | 異쒕젰 理쒖냼??|
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | ?꾩?留??쒖떆 |

### 紐⑤뱢 吏??鍮뚮뱶

?뱀젙 紐⑤뱢留?鍮뚮뱶?????덈떎. ?섏〈 紐⑤뱢? ?먮룞?쇰줈 ?ы븿?쒕떎 (`-am`).

```bash
# ?⑥씪 紐⑤뱢
./bin/build.sh was-admin
bin\build.bat was-admin
.\bin\build.ps1 -Module was-admin

# 蹂듭닔 紐⑤뱢
./bin/build.sh was-webadmin was-bootstrap
bin\build.bat was-webadmin was-bootstrap
.\bin\build.ps1 -Module was-webadmin,was-bootstrap

# ?묐몢??"was-" ?앸왂 媛??
./bin/build.sh admin webadmin
```

### ?꾩껜 紐⑤뱢 紐⑸줉

| 紐⑤뱢紐?| ?ㅻ챸 |
|--------|------|
| `was-config` | ?쒕쾭 ?ㅼ젙 (YAML ?뚯떛, ?곗씠???대옒?? |
| `was-observability` | 硫뷀듃由??섏쭛, 濡쒓퉭 |
| `was-protocol-http` | HTTP/1.1, HTTP/2 ?꾨줈?좎퐳 泥섎━ |
| `was-transport-netty` | Netty ?꾩넚 ?덉씠??|
| `was-servlet-core` | Jakarta Servlet 6.1 援ы쁽 |
| `was-classloader` | ?좏뵆由ъ??댁뀡 ?대옒?ㅻ줈??寃⑸━ |
| `was-deploy` | WAR 諛고룷, ???뷀뵆濡쒖씠 |
| `was-jndi` | JNDI ?붾젆?좊━ ?쒕퉬??|
| `was-admin` | CLI 愿由щ룄援?(73媛?紐낅졊?? |
| `was-jsp` | JSP ?붿쭊 |
| `was-tcp-listener` | TCP ?뚯폆 由ъ뒪??|
| `was-webadmin` | ??愿由?肄섏넄 |
| `was-bootstrap` | 遺?몄뒪?몃옪, Fat JAR 吏꾩엯??|

### 鍮뚮뱶 ?덉떆

```bash
# ?꾩껜 ?대┛ 鍮뚮뱶 + ?뚯뒪??
./bin/build.sh -c -t

# ?꾩껜 ?⑦궎吏?(Fat JAR ?앹꽦)
./bin/build.sh -p

# was-webadmin留?議곗슜??鍮뚮뱶
./bin/build.sh -q was-webadmin

# Windows CMD: ?대┛ ?⑦궎吏?
bin\build.bat -c -p

# PowerShell: ?꾩껜 ?대┛ 鍮뚮뱶 + ?뚯뒪??+ ?⑦궎吏?
.\bin\build.ps1 -Clean -Test -Package
```

### Fat JAR ?꾩튂

?⑦궎吏??꾨즺 ??Fat JAR 寃쎈줈:

```
was-bootstrap/target/was-bootstrap-0.5.8-jar-with-dependencies.jar
```

---

## 湲곕룞 ?ㅽ겕由쏀듃

### 湲곕낯 ?ъ슜踰?

```bash
# Linux / macOS ???ш렇?쇱슫??
./bin/start.sh

# Linux / macOS ???곕が 紐⑤뱶
./bin/start.sh -d

# Windows CMD
bin\start.bat
bin\start.bat -d

# Windows PowerShell
.\bin\start.ps1
.\bin\start.ps1 -Daemon
```

### ?듭뀡

| ?듭뀡 | sh | bat | ps1 | ?ㅻ챸 |
|------|-----|-----|-----|------|
| Config | `-c <path>`, `--config <path>` | `-c <path>`, `--config <path>` | `-Config <path>` | ?ㅼ젙 ?뚯씪 寃쎈줈 (湲곕낯: `conf/server.yaml`) |
| Daemon | `-d`, `--daemon` | `-d`, `--daemon` | `-Daemon` | 諛깃렇?쇱슫???ㅽ뻾 |
| JVM ?듭뀡 | `-j <opts>`, `--jvm-opts <opts>` | `-j <opts>`, `--jvm-opts <opts>` | `-JvmOpts <opts>` | JVM ?듭뀡 (湲곕낯: `-Xms256m -Xmx1g -XX:+UseZGC`) |
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | ?꾩?留??쒖떆 |

### ?섍꼍蹂??

| ?섍꼍蹂??| ?ㅻ챸 | 湲곕낯媛?|
|----------|------|--------|
| `JAVA_HOME` | JDK 寃쎈줈 (濡쒖뺄 ?꾧뎄泥댁씤 ?놁쓣 ?? | - |
| `VELO_JVM_OPTS` | JVM ?듭뀡 ?ㅻ쾭?쇱씠??| `-Xms256m -Xmx1g -XX:+UseZGC` |
| `VELO_CONFIG` | ?ㅼ젙 ?뚯씪 寃쎈줈 ?ㅻ쾭?쇱씠??(bat留??대떦) | `conf/server.yaml` |

### ?숈옉 諛⑹떇

1. **Fat JAR ?먮룞 鍮뚮뱶**: Fat JAR媛 ?놁쑝硫?`build.sh -p -q`瑜??먮룞 ?ㅽ뻾
2. **PID 愿由?*: ?곕が 紐⑤뱶 ??`velo-was.pid` ?뚯씪???꾨줈?몄뒪 ID 湲곕줉
3. **以묐났 湲곕룞 諛⑹?**: PID ?뚯씪??議댁옱?섍퀬 ?대떦 ?꾨줈?몄뒪媛 ?ㅽ뻾 以묒씠硫?湲곕룞 嫄곕?
4. **濡쒓렇 異쒕젰**: ?곕が 紐⑤뱶 ??`logs/velo-was.out`, `logs/velo-was.err`??異쒕젰

### 湲곕룞 ?덉떆

```bash
# 湲곕낯 ?ш렇?쇱슫??湲곕룞
./bin/start.sh

# ?댁쁺 ?ㅼ젙?쇰줈 ?곕が 湲곕룞
./bin/start.sh -d -c conf/prod.yaml

# 硫붾え由?4GB, ZGC濡?湲곕룞
./bin/start.sh -d -j "-Xms1g -Xmx4g -XX:+UseZGC"

# Windows PowerShell: ?곕が 湲곕룞
.\bin\start.ps1 -Daemon -Config conf\prod.yaml

# Windows CMD: ?곕が 湲곕룞
bin\start.bat -d -c conf\prod.yaml
```

### JVM ?쒖뒪???꾨줈?쇳떚

湲곕룞 ???먮룞?쇰줈 ?ㅼ젙?섎뒗 JVM ?꾨줈?쇳떚:

| ?꾨줈?쇳떚 | 媛?|
|----------|-----|
| `-Dvelo.config` | ?ㅼ젙 ?뚯씪 寃쎈줈 |
| `-Dvelo.home` | ?꾨줈?앺듃 猷⑦듃 寃쎈줈 |

---

## 醫낅즺 ?ㅽ겕由쏀듃

### 湲곕낯 ?ъ슜踰?

```bash
# Linux / macOS
./bin/stop.sh

# Windows CMD
bin\stop.bat

# Windows PowerShell
.\bin\stop.ps1
```

### ?듭뀡

| ?듭뀡 | sh | bat | ps1 | ?ㅻ챸 |
|------|-----|-----|-----|------|
| Force | `-f`, `--force` | `-f`, `--force` | `-Force` | 利됱떆 媛뺤젣 醫낅즺 |
| Timeout | `-t <sec>`, `--timeout <sec>` | - | `-Timeout <sec>` | Graceful 醫낅즺 ?湲??쒓컙 (湲곕낯: 30珥? |
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | ?꾩?留??쒖떆 |

### 醫낅즺 諛⑹떇

#### Graceful Shutdown (湲곕낯)

1. PID ?뚯씪 ?뺤씤 ???꾨줈?몄뒪 議댁옱 ?뺤씤
2. SIGTERM ?꾩넚 (Linux/macOS) ?먮뒗 `taskkill` (Windows)
3. 理쒕? 30珥??湲?(5珥?媛꾧꺽?쇰줈 吏꾪뻾 ?곹솴 異쒕젰)
4. ??꾩븘????SIGKILL / 媛뺤젣 醫낅즺

#### Force Kill (`-f`)

PID ?뚯씪 ?먮뒗 ?꾨줈?몄뒪 ?먯깋 ??利됱떆 SIGKILL / `taskkill /F` ?ㅽ뻾.

### ?꾨줈?몄뒪 ?먯깋 ?쒖꽌

| ?쒖쐞 | 諛⑸쾿 | ?ㅻ챸 |
|------|------|------|
| 1 | PID ?뚯씪 | `velo-was.pid` ?뚯씪 ?뺤씤 |
| 2 | ?꾨줈?몄뒪 寃??| `was-bootstrap.*jar-with-dependencies` 紐낅졊以??⑦꽩 留ㅼ묶 |

### 醫낅즺 ?덉떆

```bash
# 湲곕낯 graceful 醫낅즺
./bin/stop.sh

# 利됱떆 媛뺤젣 醫낅즺
./bin/stop.sh -f

# 10珥???꾩븘?껋쑝濡?醫낅즺
./bin/stop.sh -t 10

# Windows CMD: 媛뺤젣 醫낅즺
bin\stop.bat -f

# PowerShell: 60珥???꾩븘??
.\bin\stop.ps1 -Timeout 60
```

---

## ?뚮옯?쇰퀎 李⑥씠??

### Linux / macOS (.sh)

- `#!/usr/bin/env bash`, `set -euo pipefail` ?ъ슜
- SIGTERM/SIGKILL ?쒓렇???ъ슜
- `pgrep` 紐낅졊?쇰줈 ?꾨줈?몄뒪 寃??
- PID ?뚯씪 湲곕컲 愿由?

### Windows CMD (.bat)

- `setlocal enabledelayedexpansion` ?ъ슜
- `taskkill` / `tasklist` 紐낅졊 ?ъ슜
- `wmic` 紐낅졊?쇰줈 ?꾨줈?몄뒪 寃??(而ㅻ㎤?쒕씪???⑦꽩 留ㅼ묶)
- `start /B` 紐낅졊?쇰줈 諛깃렇?쇱슫???ㅽ뻾

### Windows PowerShell (.ps1)

- `$ErrorActionPreference = "Stop"` ?ъ슜
- `Start-Process` / `Stop-Process` cmdlet ?ъ슜
- `Get-WmiObject Win32_Process` ?먮뒗 `Get-Process`濡??꾨줈?몄뒪 寃??
- `Start-Process -WindowStyle Hidden -PassThru`濡?諛깃렇?쇱슫???ㅽ뻾
- 湲곕룞 2珥????꾨줈?몄뒪 ?앹〈 ?뺤씤

---

## ?댁쁺 ?쒕굹由ъ삤

### 媛쒕컻 ?섍꼍

```bash
# 鍮뚮뱶 ???ш렇?쇱슫??湲곕룞 (Ctrl+C濡?醫낅즺)
./bin/build.sh && ./bin/start.sh
```

### ?ㅽ뀒?댁쭠/?댁쁺 ?섍꼍

```bash
# ?대┛ 鍮뚮뱶 + ?⑦궎吏?
./bin/build.sh -c -p

# ?곕が 湲곕룞
./bin/start.sh -d -c conf/prod.yaml -j "-Xms2g -Xmx4g -XX:+UseZGC"

# ?곹깭 ?뺤씤
curl http://localhost:8080/admin/api/status

# graceful 醫낅즺
./bin/stop.sh

# 濡ㅻ쭅 ?ъ떆??
./bin/stop.sh && ./bin/start.sh -d -c conf/prod.yaml
```

### ?⑥씪 紐⑤뱢 ?섏젙 ??鍮좊Ⅸ 諛섏쁺

```bash
# webadmin留??щ퉴?????⑦궎吏????ш린??
./bin/stop.sh
./bin/build.sh -p was-webadmin was-bootstrap
./bin/start.sh -d
```

