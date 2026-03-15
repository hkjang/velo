<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.io.*, java.net.*, java.nio.*, java.nio.charset.*, java.time.*, java.time.format.*" %>
<%!
    // 전문 상수
    static final int HEADER_LEN = 60;
    static final Charset CS = StandardCharsets.UTF_8;
    static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    static String padR(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }
    static String padL(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        return "0".repeat(len - s.length()) + s;
    }

    /** 전문 헤더 조립 */
    static String buildHeader(String msgCode, String bizCode, String senderId,
                               String receiverId, String traceNo) {
        return padR(msgCode, 4) + padR(bizCode, 4) + padR(senderId, 10)
             + padR(receiverId, 10) + padR(traceNo, 20)
             + padR(LocalDateTime.now().format(TS_FMT), 12);
    }

    /** TCP 전문 송수신 (4바이트 길이 헤더 프로토콜) */
    static String sendTcpMessage(String host, int port, byte[] payload, int timeoutMs) throws Exception {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), timeoutMs);
            sock.setSoTimeout(timeoutMs);

            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            // 4바이트 길이 헤더 + payload 전송
            int len = payload.length;
            byte[] lengthHeader = new byte[4];
            lengthHeader[0] = (byte) ((len >> 24) & 0xFF);
            lengthHeader[1] = (byte) ((len >> 16) & 0xFF);
            lengthHeader[2] = (byte) ((len >> 8) & 0xFF);
            lengthHeader[3] = (byte) (len & 0xFF);

            out.write(lengthHeader);
            out.write(payload);
            out.flush();

            // 응답 수신: 4바이트 길이 헤더 읽기
            byte[] respLenBuf = new byte[4];
            int read = 0;
            while (read < 4) {
                int r = in.read(respLenBuf, read, 4 - read);
                if (r < 0) throw new IOException("Connection closed while reading length header");
                read += r;
            }
            int respLen = ((respLenBuf[0] & 0xFF) << 24)
                        | ((respLenBuf[1] & 0xFF) << 16)
                        | ((respLenBuf[2] & 0xFF) << 8)
                        | (respLenBuf[3] & 0xFF);

            if (respLen <= 0 || respLen > 1048576) {
                throw new IOException("Invalid response length: " + respLen);
            }

            // 응답 본문 읽기
            byte[] respBody = new byte[respLen];
            read = 0;
            while (read < respLen) {
                int r = in.read(respBody, read, respLen - read);
                if (r < 0) throw new IOException("Connection closed while reading response body");
                read += r;
            }
            return new String(respBody, CS);
        }
    }

    /** 응답 전문 파싱 (HTML 테이블로) */
    static String parseResponse(String resp) {
        StringBuilder sb = new StringBuilder();
        if (resp.length() < HEADER_LEN) {
            sb.append("<tr><td colspan='2'>응답 길이 부족 (").append(resp.length()).append(" bytes)</td></tr>");
            sb.append("<tr><td>Raw</td><td><code>").append(escHtml(resp)).append("</code></td></tr>");
            return sb.toString();
        }
        sb.append("<tr><td>전문종별코드</td><td><code>").append(escHtml(resp.substring(0, 4))).append("</code></td></tr>");
        sb.append("<tr><td>업무구분코드</td><td><code>").append(escHtml(resp.substring(4, 8))).append("</code></td></tr>");
        sb.append("<tr><td>송신기관코드</td><td><code>").append(escHtml(resp.substring(8, 18))).append("</code></td></tr>");
        sb.append("<tr><td>수신기관코드</td><td><code>").append(escHtml(resp.substring(18, 28))).append("</code></td></tr>");
        sb.append("<tr><td>전문추적번호</td><td><code>").append(escHtml(resp.substring(28, 48))).append("</code></td></tr>");
        sb.append("<tr><td>전송일시</td><td><code>").append(escHtml(resp.substring(48, 60))).append("</code></td></tr>");

        // Body part
        String body = resp.substring(HEADER_LEN);
        if (body.length() >= 24) {
            sb.append("<tr class='body-row'><td>응답코드</td><td><code>").append(escHtml(body.substring(0, 4))).append("</code></td></tr>");
            sb.append("<tr class='body-row'><td>응답메시지</td><td><code>").append(escHtml(body.substring(4, 24))).append("</code></td></tr>");
            String extra = body.substring(24);
            if (!extra.isBlank()) {
                sb.append("<tr class='body-row'><td>응답데이터</td><td><code>").append(escHtml(extra)).append("</code></td></tr>");
            }
        } else if (!body.isEmpty()) {
            sb.append("<tr class='body-row'><td>응답Body</td><td><code>").append(escHtml(body)).append("</code></td></tr>");
        }
        return sb.toString();
    }

    static String escHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
%>
<%
    String tcpHost = "127.0.0.1";
    int tcpPort = 9090;
    int timeout = 5000;

    String action = request.getParameter("action");
    String resultHtml = null;
    String rawSent = null;
    String rawResp = null;

    if (action != null && !action.isEmpty()) {
        try {
            String senderId  = request.getParameter("senderId") != null ? request.getParameter("senderId") : "TESTORG001";
            String receiverId= request.getParameter("receiverId") != null ? request.getParameter("receiverId") : "VELOSVR001";
            String traceNo   = request.getParameter("traceNo") != null ? request.getParameter("traceNo") : String.valueOf(System.currentTimeMillis());

            byte[] payload;
            String headerStr = buildHeader("0200", action, senderId, receiverId, traceNo);

            switch (action) {
                case "INQY": {
                    String acctNo = request.getParameter("accountNo");
                    payload = (headerStr + padR(acctNo, 20)).getBytes(CS);
                    break;
                }
                case "BLNC": {
                    String acctNo = request.getParameter("accountNo");
                    payload = (headerStr + padR(acctNo, 20)).getBytes(CS);
                    break;
                }
                case "TRFR": {
                    String fromAcct = request.getParameter("fromAccount");
                    String toAcct   = request.getParameter("toAccount");
                    String amount   = request.getParameter("amount");
                    String remark   = request.getParameter("remark");
                    String body = padR(fromAcct, 20) + padR(toAcct, 20) + padL(amount, 15) + padR(remark, 20);
                    payload = (headerStr + body).getBytes(CS);
                    break;
                }
                default:
                    throw new IllegalArgumentException("미지원 업무코드: " + action);
            }

            rawSent = new String(payload, CS);
            String resp = sendTcpMessage(tcpHost, tcpPort, payload, timeout);
            rawResp = resp;
            resultHtml = parseResponse(resp);
        } catch (Exception e) {
            resultHtml = "<tr><td colspan='2' class='error'>오류: " + escHtml(e.getMessage()) + "</td></tr>";
        }
    }
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>TCP 금융전문 테스트</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'Malgun Gothic', 'Nanum Gothic', sans-serif; background: #f4f6f9; color: #333; }
        .container { max-width: 1100px; margin: 0 auto; padding: 24px; }
        h1 { font-size: 22px; color: #1a237e; margin-bottom: 6px; }
        .subtitle { color: #666; font-size: 13px; margin-bottom: 24px; }
        .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
        .card { background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.1); padding: 20px; }
        .card h2 { font-size: 16px; color: #283593; margin-bottom: 14px; border-bottom: 2px solid #3949ab; padding-bottom: 6px; }
        .field { margin-bottom: 12px; }
        .field label { display: block; font-size: 12px; color: #555; margin-bottom: 3px; font-weight: 600; }
        .field input, .field select { width: 100%; padding: 8px 10px; border: 1px solid #ccc; border-radius: 4px; font-size: 13px; font-family: 'Consolas', monospace; }
        .field input:focus { border-color: #3949ab; outline: none; box-shadow: 0 0 0 2px rgba(57,73,171,0.15); }
        .btn { display: inline-block; padding: 9px 22px; background: #3949ab; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 13px; font-weight: 600; }
        .btn:hover { background: #283593; }
        .btn-group { margin-top: 6px; }
        .sample-accounts { background: #f8f9fa; border: 1px solid #e0e0e0; border-radius: 4px; padding: 10px; font-size: 12px; margin-bottom: 14px; }
        .sample-accounts code { background: #e8eaf6; padding: 1px 5px; border-radius: 3px; font-size: 12px; }

        .result-area { grid-column: 1 / -1; }
        .result-area h2 { color: #1b5e20; border-color: #43a047; }
        table.result { width: 100%; border-collapse: collapse; font-size: 13px; }
        table.result td { padding: 6px 10px; border-bottom: 1px solid #eee; }
        table.result td:first-child { width: 130px; font-weight: 600; color: #555; background: #fafafa; }
        table.result code { font-family: 'Consolas', monospace; font-size: 13px; background: #e8f5e9; padding: 1px 4px; border-radius: 2px; }
        tr.body-row td { background: #f1f8e9 !important; }
        tr.body-row td:first-child { color: #33691e; }
        .raw-section { margin-top: 14px; }
        .raw-section summary { cursor: pointer; font-size: 12px; color: #666; }
        .raw-box { background: #263238; color: #e0e0e0; padding: 12px; border-radius: 4px; font-family: 'Consolas', monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; margin-top: 6px; line-height: 1.6; }
        .raw-box .offset { color: #78909c; }
        .raw-box .header-part { color: #80cbc4; }
        .raw-box .body-part { color: #fff59d; }
        .error { color: #c62828; background: #ffebee !important; font-weight: 600; }
        .protocol-info { font-size: 11px; color: #888; margin-top: 8px; }

        .tabs { display: flex; gap: 0; margin-bottom: 0; }
        .tab { padding: 8px 18px; background: #e8eaf6; border: 1px solid #c5cae9; border-bottom: none; border-radius: 6px 6px 0 0; cursor: pointer; font-size: 13px; font-weight: 600; color: #5c6bc0; }
        .tab.active { background: #fff; color: #283593; border-bottom: 1px solid #fff; margin-bottom: -1px; z-index: 1; }
        .tab-content { display: none; }
        .tab-content.active { display: block; }
    </style>
</head>
<body>
<div class="container">
    <h1>TCP 금융전문 테스트 콘솔</h1>
    <p class="subtitle">Velo WAS TCP Listener &mdash; 금융권 표준 전문 송수신 테스트 (Length-Prefixed Protocol, port <%= tcpPort %>)</p>

    <div class="sample-accounts">
        <strong>테스트 계좌:</strong>
        <code>1002345678901234</code> 홍길동 (15,000,000원) &nbsp;|&nbsp;
        <code>1109876543210987</code> 김영희 (8,500,000원) &nbsp;|&nbsp;
        <code>2201122334455667</code> 이철수 (32,000,000원)
    </div>

    <div class="tabs">
        <div class="tab active" onclick="switchTab('inqy')">계좌조회 (INQY)</div>
        <div class="tab" onclick="switchTab('blnc')">잔액조회 (BLNC)</div>
        <div class="tab" onclick="switchTab('trfr')">계좌이체 (TRFR)</div>
    </div>

    <div class="grid" style="border-top: 1px solid #c5cae9;">

        <!-- 계좌조회 (INQY) -->
        <div class="card tab-content active" id="tab-inqy" style="grid-column: 1/-1;">
            <h2>계좌조회 (INQY)</h2>
            <form method="post">
                <input type="hidden" name="action" value="INQY">
                <div class="grid" style="gap:12px;">
                    <div>
                        <div class="field">
                            <label>송신기관코드 (10자리)</label>
                            <input name="senderId" value="TESTORG001" maxlength="10">
                        </div>
                        <div class="field">
                            <label>수신기관코드 (10자리)</label>
                            <input name="receiverId" value="VELOSVR001" maxlength="10">
                        </div>
                    </div>
                    <div>
                        <div class="field">
                            <label>전문추적번호</label>
                            <input name="traceNo" value="<%= System.currentTimeMillis() %>" maxlength="20">
                        </div>
                        <div class="field">
                            <label>조회 계좌번호 (20자리)</label>
                            <input name="accountNo" value="1002345678901234" maxlength="20" placeholder="계좌번호 입력">
                        </div>
                    </div>
                </div>
                <div class="btn-group"><button class="btn" type="submit">전문 송신</button></div>
                <p class="protocol-info">전문구조: [4B길이헤더][Header 60B: 종별(4)+업무(4)+송신(10)+수신(10)+추적(20)+일시(12)][Body: 계좌번호(20)]</p>
            </form>
        </div>

        <!-- 잔액조회 (BLNC) -->
        <div class="card tab-content" id="tab-blnc" style="grid-column: 1/-1;">
            <h2>잔액조회 (BLNC)</h2>
            <form method="post">
                <input type="hidden" name="action" value="BLNC">
                <div class="grid" style="gap:12px;">
                    <div>
                        <div class="field">
                            <label>송신기관코드</label>
                            <input name="senderId" value="TESTORG001" maxlength="10">
                        </div>
                        <div class="field">
                            <label>수신기관코드</label>
                            <input name="receiverId" value="VELOSVR001" maxlength="10">
                        </div>
                    </div>
                    <div>
                        <div class="field">
                            <label>전문추적번호</label>
                            <input name="traceNo" value="<%= System.currentTimeMillis() %>" maxlength="20">
                        </div>
                        <div class="field">
                            <label>조회 계좌번호</label>
                            <input name="accountNo" value="1109876543210987" maxlength="20" placeholder="계좌번호 입력">
                        </div>
                    </div>
                </div>
                <div class="btn-group"><button class="btn" type="submit">전문 송신</button></div>
                <p class="protocol-info">전문구조: [4B길이헤더][Header 60B][Body: 계좌번호(20)]</p>
            </form>
        </div>

        <!-- 계좌이체 (TRFR) -->
        <div class="card tab-content" id="tab-trfr" style="grid-column: 1/-1;">
            <h2>계좌이체 (TRFR)</h2>
            <form method="post">
                <input type="hidden" name="action" value="TRFR">
                <div class="grid" style="gap:12px;">
                    <div>
                        <div class="field">
                            <label>송신기관코드</label>
                            <input name="senderId" value="TESTORG001" maxlength="10">
                        </div>
                        <div class="field">
                            <label>수신기관코드</label>
                            <input name="receiverId" value="VELOSVR001" maxlength="10">
                        </div>
                        <div class="field">
                            <label>전문추적번호</label>
                            <input name="traceNo" value="<%= System.currentTimeMillis() %>" maxlength="20">
                        </div>
                    </div>
                    <div>
                        <div class="field">
                            <label>출금계좌</label>
                            <input name="fromAccount" value="1002345678901234" maxlength="20">
                        </div>
                        <div class="field">
                            <label>입금계좌</label>
                            <input name="toAccount" value="1109876543210987" maxlength="20">
                        </div>
                        <div class="field">
                            <label>이체금액 (원)</label>
                            <input name="amount" value="1000000" maxlength="15">
                        </div>
                        <div class="field">
                            <label>적요</label>
                            <input name="remark" value="월급이체" maxlength="20">
                        </div>
                    </div>
                </div>
                <div class="btn-group"><button class="btn" type="submit">전문 송신</button></div>
                <p class="protocol-info">전문구조: [4B길이헤더][Header 60B][Body: 출금계좌(20)+입금계좌(20)+금액(15)+적요(20)]</p>
            </form>
        </div>

        <% if (resultHtml != null) { %>
        <div class="card result-area">
            <h2>응답 결과</h2>
            <table class="result">
                <%= resultHtml %>
            </table>

            <div class="raw-section">
                <details>
                    <summary>전문 원시 데이터 보기</summary>
                    <% if (rawSent != null) { %>
                    <p style="font-size:12px; margin-top:8px; color:#555;"><strong>송신 전문</strong> (<%= rawSent.length() %> bytes)</p>
                    <div class="raw-box"><span class="header-part"><%= escHtml(rawSent.substring(0, Math.min(HEADER_LEN, rawSent.length()))) %></span><span class="body-part"><%= rawSent.length() > HEADER_LEN ? escHtml(rawSent.substring(HEADER_LEN)) : "" %></span></div>
                    <% } %>
                    <% if (rawResp != null) { %>
                    <p style="font-size:12px; margin-top:8px; color:#555;"><strong>수신 전문</strong> (<%= rawResp.length() %> bytes)</p>
                    <div class="raw-box"><span class="header-part"><%= escHtml(rawResp.substring(0, Math.min(HEADER_LEN, rawResp.length()))) %></span><span class="body-part"><%= rawResp.length() > HEADER_LEN ? escHtml(rawResp.substring(HEADER_LEN)) : "" %></span></div>
                    <% } %>
                </details>
            </div>
        </div>
        <% } %>

    </div>
</div>

<script>
function switchTab(id) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
    document.getElementById('tab-' + id).classList.add('active');
    event.target.classList.add('active');
}
</script>
</body>
</html>
