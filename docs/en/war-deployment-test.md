# WAR Deployment & JSP Testing Guide

This document guides you through deploying `test-war` and `test-app.war` in the Velo WAS project and verifying that the Servlet and JSP endpoints function correctly.

## System Requirements

- Java 21+
- Maven (for building)
- curl utility (for testing HTTP requests)

## 1. Preparation & Build

To test the deployment and Hot Deploy features, you must enable the feature in the server configuration file (`conf/server.yaml`).

Open `conf/server.yaml` and set `hotDeploy` to `true`:

```yaml
deploy:
  directory: deploy
  hotDeploy: true
  scanIntervalSeconds: 2
```

After modifying the configuration, build the entire Velo WAS platform.

```sh
# Build the Velo WAS platform
mvn clean package -DskipTests
```

## 2. Server Startup

Start the Velo WAS server in the background or in a new terminal window with the following command:

```sh
java -jar was-bootstrap/target/was-bootstrap-0.1.0-SNAPSHOT-jar-with-dependencies.jar
```

When the `Hot deploy watcher started` message appears in the server startup logs, the server is successfully monitoring the `deploy` directory for automatic deployments.

---

## 3. Servlet Project Deployment Test (`test.war`)

The `test-war` module is a project intended to verify basic Servlet and Filter functionality.

### 3.1. Packaging & Deployment

Navigate to the `test-war` directory, package it, and copy it to the server's `deploy` directory.

```sh
# Build test-war
cd test-war
mvn clean package

# Copy the artifact to the Velo WAS deploy directory
cp target/test-war-0.1.0-SNAPSHOT.war ../deploy/test.war
```

Since `hotDeploy` is enabled, a successful deployment message will appear in the server logs almost immediately (within ~2 seconds).

### 3.2. Endpoint Verification

Once deployed, verify the Servlet response using the `curl` command.

```sh
$ curl -s http://localhost:8080/test/hello

Hello from TestServlet! User-Agent: curl/8.x.x
```

![TestServlet Output](../images/test_war_hello.png)

If the response is properly printed out, it means the Servlet mappings in `test.war` are functioning properly.

---

## 4. JSP Project Deployment Test (`test-app`)

`test-app` is a project aimed at validating JSP file parsing, Java code generation (Translation), and the `JspServlet` mapping.

### 4.1. Project Structure

```text
test-app/
├── index.jsp         # Starting page
├── info.jsp          # System information page (contains <%@ page import="..." %>)
└── WEB-INF/
    └── web.xml       # Servlet & Deployment definitions
```

### 4.2. Packaging & Deployment

Navigate to the `test-app` directory and package the folder into a WAR format, then deploy it.

```sh
cd ../test-app

# Manually archive into a WAR file (using the jar command)
jar -cvf ../deploy/test-app.war *
```

Upon successful deployment, the following will appear in the loading logs:
> `INFO io.velo.was.deploy.WarDeployer - WAR deployed: name=test-app contextPath=/test-app source=deploy\test-app.war...`

### 4.3. JSP Endpoint Verification

If successfully deployed, check if the JSP files dynamically render HTML by requesting the following URLs.

```sh
# 1. Main Page Test
$ curl -s http://localhost:8080/test-app/index.jsp

<!DOCTYPE html>
<html>
<head>
    <title>Velo WAS - Test App</title>
</head>
<body>
    <h1>Welcome to Velo WAS Test Application</h1>
    <p>This page is rendered by the JSP Engine.</p>
...
</html>
```

![test-app Main Index Page](../images/test_app_index.png)

```sh
# 2. Server Information Test (Referencing internal JVM objects)
$ curl -s http://localhost:8080/test-app/info.jsp

<!DOCTYPE html>
<html>
...
    <table border="1" cellpadding="8" cellspacing="0">
        <tr><th>Item</th><th>Value</th></tr>
        <tr><td>Current Time</td><td>2026-03-15T11:03:34.840</td></tr>
...
</html>
```

![test-app Server Information Page](../images/test_app_info.png)

If you receive normal 200 HTTP response codes and the HTML body correctly displays without any `No servlet mapping` or `500 Server Error` errors, it signifies that JSP compilation, translation, and classloader integration have completely succeeded.
