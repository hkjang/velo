package io.velo.was.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;

final class DispatchBridgeSupport {

    private DispatchBridgeSupport() {
    }

    static InternalRequestBridge requestBridge(ServletRequest request) throws ServletException {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper) {
            current = wrapper.getRequest();
        }
        if (current instanceof InternalRequestBridge bridge) {
            return bridge;
        }
        throw new ServletException("Unsupported request type for dispatch: " + request.getClass().getName());
    }

    static InternalResponseBridge responseBridge(ServletResponse response) throws ServletException {
        ServletResponse current = response;
        while (current instanceof ServletResponseWrapper wrapper) {
            current = wrapper.getResponse();
        }
        if (current instanceof InternalResponseBridge bridge) {
            return bridge;
        }
        throw new ServletException("Unsupported response type for dispatch: " + response.getClass().getName());
    }

    static boolean hasOriginalRequestAndResponse(ServletRequest request, ServletResponse response) {
        return request instanceof InternalRequestBridge && response instanceof InternalResponseBridge;
    }

    static Binding<ServletRequest> bindRequest(ServletRequest source, HttpServletRequest target) throws ServletException {
        if (source == null || source instanceof InternalRequestBridge) {
            return new Binding<>(target, () -> {
            });
        }

        List<ServletRequestWrapper> wrappers = new ArrayList<>();
        ServletRequest current = source;
        while (current instanceof ServletRequestWrapper wrapper) {
            wrappers.add(wrapper);
            current = wrapper.getRequest();
        }
        if (!(current instanceof InternalRequestBridge)) {
            throw new ServletException("Unsupported request wrapper chain for dispatch: " + source.getClass().getName());
        }

        ServletRequestWrapper innermost = wrappers.get(wrappers.size() - 1);
        ServletRequest original = innermost.getRequest();
        innermost.setRequest(target);
        return new Binding<>(wrappers.get(0), () -> innermost.setRequest(original));
    }

    static Binding<ServletResponse> bindResponse(ServletResponse source, HttpServletResponse target) throws ServletException {
        if (source == null || source instanceof InternalResponseBridge) {
            return new Binding<>(target, () -> {
            });
        }

        List<ServletResponseWrapper> wrappers = new ArrayList<>();
        ServletResponse current = source;
        while (current instanceof ServletResponseWrapper wrapper) {
            wrappers.add(wrapper);
            current = wrapper.getResponse();
        }
        if (!(current instanceof InternalResponseBridge)) {
            throw new ServletException("Unsupported response wrapper chain for dispatch: " + source.getClass().getName());
        }

        ServletResponseWrapper innermost = wrappers.get(wrappers.size() - 1);
        ServletResponse original = innermost.getResponse();
        innermost.setResponse(target);
        return new Binding<>(wrappers.get(0), () -> innermost.setResponse(original));
    }

    record Binding<T>(T exposed, Runnable restore) {
    }
}
