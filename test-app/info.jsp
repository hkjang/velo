<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.lang.management.ManagementFactory" %>
<%@ page import="java.time.LocalDateTime" %>
<%@ page import="java.time.Duration" %>
<!DOCTYPE html>
<html>
<head>
    <title>Server Info</title>
</head>
<body>
    <h1>Server Information</h1>

    <%
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration uptime = Duration.ofMillis(uptimeMs);
        long totalMb = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMb = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMb = totalMb - freeMb;
    %>

    <table border="1" cellpadding="8" cellspacing="0">
        <tr><th>항목</th><th>값</th></tr>
        <tr><td>현재 시간</td><td><%= LocalDateTime.now() %></td></tr>
        <tr><td>JVM 가동 시간</td><td><%= uptime.toHours() %>시간 <%= uptime.toMinutesPart() %>분 <%= uptime.toSecondsPart() %>초</td></tr>
        <tr><td>Java 버전</td><td><%= System.getProperty("java.version") %></td></tr>
        <tr><td>OS</td><td><%= System.getProperty("os.name") %> <%= System.getProperty("os.arch") %></td></tr>
        <tr><td>사용 메모리</td><td><%= usedMb %> MB / <%= totalMb %> MB</td></tr>
        <tr><td>여유 메모리</td><td><%= freeMb %> MB</td></tr>
        <tr><td>활성 스레드</td><td><%= Thread.activeCount() %></td></tr>
        <tr><td>프로세서 수</td><td><%= Runtime.getRuntime().availableProcessors() %></td></tr>
    </table>

    <br/>
    <a href="index.jsp">← 메인으로</a>
</body>
</html>
