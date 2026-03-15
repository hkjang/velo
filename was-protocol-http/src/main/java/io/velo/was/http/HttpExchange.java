package io.velo.was.http;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.net.SocketAddress;

public record HttpExchange(
        FullHttpRequest request,
        SocketAddress remoteAddress,
        SocketAddress localAddress,
        ResponseSink responseSink,
        boolean secure
) {
    public HttpExchange(FullHttpRequest request, SocketAddress remoteAddress, SocketAddress localAddress,
                        ResponseSink responseSink) {
        this(request, remoteAddress, localAddress, responseSink, false);
    }

    public HttpExchange(FullHttpRequest request, SocketAddress remoteAddress, SocketAddress localAddress) {
        this(request, remoteAddress, localAddress, null, false);
    }

    public String uri() {
        return request.uri();
    }

    public String path() {
        return new QueryStringDecoder(request.uri()).path();
    }

    public HttpHeaders headers() {
        return request.headers();
    }
}
