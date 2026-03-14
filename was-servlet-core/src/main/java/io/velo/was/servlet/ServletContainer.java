package io.velo.was.servlet;

import io.velo.was.http.HttpExchange;
import io.netty.handler.codec.http.FullHttpResponse;

public interface ServletContainer {
    void deploy(ServletApplication application) throws Exception;
    void undeploy(String applicationName) throws Exception;
    FullHttpResponse handle(HttpExchange exchange);
}
