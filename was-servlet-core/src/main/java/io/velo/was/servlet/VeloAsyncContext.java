package io.velo.was.servlet;

import io.netty.handler.codec.http.FullHttpResponse;
import io.velo.was.http.ResponseSink;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class VeloAsyncContext implements AsyncContext {

    private final ServletRequest request;
    private final ServletResponse response;
    private final ServletResponseContext responseContext;
    private final SessionHolder sessionHolder;
    private final String httpMethod;
    private final ResponseSink responseSink;
    private final AsyncDispatcher dispatcher;
    private final ScheduledExecutorService scheduler;
    private final List<AsyncListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private volatile long timeout = 30000;
    private volatile ScheduledFuture<?> timeoutFuture;

    VeloAsyncContext(ServletRequest request,
                     ServletResponse response,
                     ServletResponseContext responseContext,
                     SessionHolder sessionHolder,
                     String httpMethod,
                     ResponseSink responseSink,
                     AsyncDispatcher dispatcher,
                     ScheduledExecutorService scheduler) {
        this.request = request;
        this.response = response;
        this.responseContext = responseContext;
        this.sessionHolder = sessionHolder;
        this.httpMethod = httpMethod;
        this.responseSink = responseSink;
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
        scheduleTimeout();
    }

    @Override
    public ServletRequest getRequest() {
        return request;
    }

    @Override
    public ServletResponse getResponse() {
        return response;
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
        return true;
    }

    @Override
    public void dispatch() {
        InternalRequestBridge bridge = (InternalRequestBridge) request;
        ServletRequestContext ctx = bridge.requestContext();
        String path = ctx.servletPath();
        if (ctx.pathInfo() != null) {
            path += ctx.pathInfo();
        }
        dispatch(ctx.servletContext(), path);
    }

    @Override
    public void dispatch(String path) {
        InternalRequestBridge bridge = (InternalRequestBridge) request;
        dispatch(bridge.requestContext().servletContext(), path);
    }

    @Override
    public void dispatch(ServletContext context, String path) {
        if (completed.get()) {
            throw new IllegalStateException("Async processing already completed");
        }
        cancelTimeout();
        scheduler.execute(() -> {
            try {
                dispatcher.dispatch(path, request, response);
                complete();
            } catch (Exception e) {
                fireOnError(e);
                try {
                    complete();
                } catch (Exception ignored) {
                }
            }
        });
    }

    @Override
    public void complete() {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        cancelTimeout();
        fireOnComplete();
        if (responseSink != null && !responseContext.isCommitted()) {
            FullHttpResponse nettyResponse = responseContext.toNettyResponse(
                    "HEAD".equals(httpMethod),
                    sessionHolder.shouldSetCookie(),
                    sessionHolder.sessionId());
            responseSink.send(nettyResponse);
        }
    }

    @Override
    public void start(Runnable run) {
        scheduler.execute(run);
    }

    @Override
    public void addListener(AsyncListener listener) {
        listeners.add(listener);
    }

    @Override
    public void addListener(AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse) {
        listeners.add(listener);
    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ServletException("Cannot create AsyncListener", e);
        }
    }

    @Override
    public void setTimeout(long timeout) {
        this.timeout = timeout;
        cancelTimeout();
        if (timeout > 0) {
            scheduleTimeout();
        }
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    private void scheduleTimeout() {
        if (timeout > 0) {
            timeoutFuture = scheduler.schedule(this::onTimeout, timeout, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelTimeout() {
        ScheduledFuture<?> future = timeoutFuture;
        if (future != null) {
            future.cancel(false);
            timeoutFuture = null;
        }
    }

    private void onTimeout() {
        if (completed.get()) {
            return;
        }
        fireOnTimeout();
        complete();
    }

    private void fireOnComplete() {
        AsyncEvent event = new AsyncEvent(this, request, response);
        for (AsyncListener listener : listeners) {
            try {
                listener.onComplete(event);
            } catch (IOException ignored) {
            }
        }
    }

    private void fireOnTimeout() {
        AsyncEvent event = new AsyncEvent(this, request, response);
        for (AsyncListener listener : listeners) {
            try {
                listener.onTimeout(event);
            } catch (IOException ignored) {
            }
        }
    }

    private void fireOnError(Throwable throwable) {
        AsyncEvent event = new AsyncEvent(this, request, response, throwable);
        for (AsyncListener listener : listeners) {
            try {
                listener.onError(event);
            } catch (IOException ignored) {
            }
        }
    }

    @FunctionalInterface
    interface AsyncDispatcher {
        void dispatch(String path, ServletRequest request, ServletResponse response) throws Exception;
    }

    interface SessionHolder {
        boolean shouldSetCookie();
        String sessionId();
    }
}
