package io.velo.was.servlet;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestListener;

import java.util.Map;
import java.util.List;

public interface ServletApplication {
    String name();
    String contextPath();
    ClassLoader classLoader();
    Map<String, Servlet> servlets();
    List<FilterRegistrationSpec> filters();
    List<ServletContextListener> servletContextListeners();
    List<ServletRequestListener> servletRequestListeners();
    default Map<String, String> initParameters() {
        return Map.of();
    }
}
