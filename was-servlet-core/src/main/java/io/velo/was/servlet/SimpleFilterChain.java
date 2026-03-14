package io.velo.was.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;
import java.util.List;

class SimpleFilterChain implements FilterChain {

    private final List<Filter> filters;
    private final Servlet servlet;
    private int index;

    SimpleFilterChain(List<Filter> filters, Servlet servlet) {
        this.filters = filters;
        this.servlet = servlet;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
            throws IOException, jakarta.servlet.ServletException {
        if (index < filters.size()) {
            Filter next = filters.get(index++);
            next.doFilter(request, response, this);
            return;
        }
        servlet.service(request, response);
    }
}
