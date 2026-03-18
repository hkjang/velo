package io.velo.was.servlet;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

import java.util.Map;
import java.util.List;

public interface ServletApplication {
    String name();
    String contextPath();
    ClassLoader classLoader();
    Map<String, Servlet> servlets();
    List<FilterRegistrationSpec> filters();
    List<ServletContextListener> servletContextListeners();
    default List<ServletContextAttributeListener> servletContextAttributeListeners() {
        return List.of();
    }
    List<ServletRequestListener> servletRequestListeners();
    default List<HttpSessionListener> httpSessionListeners() {
        return List.of();
    }
    default List<HttpSessionAttributeListener> httpSessionAttributeListeners() {
        return List.of();
    }
    default List<HttpSessionIdListener> httpSessionIdListeners() {
        return List.of();
    }
    default List<ErrorPageSpec> errorPages() {
        return List.of();
    }
    default Map<String, String> initParameters() {
        return Map.of();
    }

    default String servletName(String mappingPath) {
        return mappingPath;
    }

    default Map<String, String> servletInitParameters(String mappingPath) {
        return Map.of();
    }

    default List<String> welcomeFiles() {
        return List.of();
    }
}
