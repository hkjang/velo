<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <title>Velo WAS - Test App</title>
</head>
<body>
    <h1>Welcome to Velo WAS Test Application</h1>
    <p>This page is rendered by the JSP Engine.</p>
    <p>Server Time: <%= java.time.LocalDateTime.now() %></p>
    <p>Java Version: <%= System.getProperty("java.version") %></p>

    <h2>Navigation</h2>
    <ul>
        <li><a href="greeting?name=Velo">Greeting Servlet</a></li>
        <li><a href="api/status">API Status (JSON)</a></li>
        <li><a href="info.jsp">Server Info (JSP)</a></li>
    </ul>
</body>
</html>
