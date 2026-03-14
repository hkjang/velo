# `was-classloader` Module Guide

The `was-classloader` module is a vital component for ensuring application isolation. In a traditional Java application, all classes are loaded sequentially into a single flat classpath. In a Servlet-compatible environment, each deployed application must have its own isolated execution environment to prevent dependency collisions.

## Key Components

### `WebAppClassLoader`
A specialized `ClassLoader` implementation targeting standard Java Servlet environments.

- **Isolation (Child-First Loading)**: Unlike the standard Java parent-first mechanism, `WebAppClassLoader` delegates strictly to `WEB-INF/classes` and `WEB-INF/lib/*.jar` *before* delegating to the server's parent classloader. This ensures that web applications can bring their own versions of libraries (e.g., a specific Guava or Spring version) without colliding with Velo WAS internals.
- **Resource Lookups**: Transparently reads standard web archive resources as streamable URLs.
- **Memory Leak Prevention**: Discards the classloader hierarchy entirely when an application is undeployed via the `was-admin` CLI, allowing the Garbage Collector to reclaim all classes and static instances initiated by the web application.
