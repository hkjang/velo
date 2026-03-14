# Velo WAS User Guide

Velo WAS is a Netty-based enterprise Web Application Server (WAS) foundation that aims to provide Tomcat-level servlet compatibility alongside Jetty-level I/O performance. This guide covers how developers can use Velo WAS for developing and deploying their web applications.

## 1. Supported Specifications
Velo WAS currently supports the **Jakarta Servlet 6.1 API** specification. It leverages Netty for asynchronous I/O and protocol handling, while maintaining a servlet-compatible container layer. 
Your application should be packaged as a standard `.war` archive.

Features supported:
- Standard `HttpServlet` base classes
- `Filter` chains and lifecycle
- Request Dispatching (`forward` and `include`)
- Servlet API Listeners
- `JSESSIONID` based in-memory sessions

## 2. Developing an Application

Add the necessary dependencies to your project (e.g., `pom.xml` if using Maven). You need the Jakarta Servlet API for compilation:

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.1.0</version>
    <scope>provided</scope>
</dependency>
```

Build your application using `mvn clean package` to produce a `.war` file in your target directory.

## 3. Deployment

Velo WAS applications can be deployed through the internal configuration file or via the `velo-admin` Command Line Interface.

### Directory Structure
To deploy manually via the config tree, place your `.war` applications or exploded directories within a known path and define them in the server's configuration YAML file (e.g., `conf/server.yaml`).

### Deploying via Admin CLI
The most efficient way to deploy applications is through the `velo-admin` interactive shell. 

1. Start the `velo-admin` shell using your preferred JVM parameters (or `bin/velo-admin.sh` if packaged).
2. Connect to the server if remote, or just run it locally.
3. Use the following deployment commands:

```shell
velo> deploy /path/to/your-app.war /yourapp
velo> list-applications
velo> start-application yourapp
```

- `deploy <war-path> <context-path>`: Deploys a new web archive to a specific context path.
- `list-applications`: Shows deployed applications, their paths, and their statuses.
- `start-application <name>`: Initializes the application layout (filters and servlets) to accept requests.

To undeploy or replace an application:
```shell
velo> redeploy yourapp
velo> undeploy yourapp
```

## 4. Application Logging
Applications use `SLF4J` as the standard abstraction. Logs will be integrated into Velo WAS's central logger. Ensure that any backend logging implementation (like `logback-classic`) does not conflict with the server's logging.

## 5. Troubleshooting Checklists
- **ClassNotFoundError**: Make sure your WAR deployment packages `.jar` files in `WEB-INF/lib` correctly. Check for classpath collisions.
- **Port Conflict**: Velo WAS binds to configured ports. Ensure the Netty listener is free. Use `server-info` on `velo-admin` to check bindings.
