package io.velo.was.bootstrap;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.velo.was.http.HttpResponses;
import io.velo.was.observability.AccessLog;
import io.velo.was.observability.AccessLogEntry;
import io.velo.was.observability.ErrorLog;
import io.velo.was.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

final class AdminWarUploadChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AdminWarUploadChannelHandler.class);

    private final AdminWarUploadService uploadService;
    private final Path stagingDirectory;

    private boolean handlingUpload;
    private boolean discardingRejectedRequest;
    private HttpRequest currentRequest;
    private HttpPostRequestDecoder decoder;
    private Path stagedFile;
    private String submittedFileName;
    private String contextPath;
    private long startedAtNanos;

    AdminWarUploadChannelHandler(AdminWarUploadService uploadService, Path stagingDirectory) {
        this.uploadService = uploadService;
        this.stagingDirectory = stagingDirectory;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpObject httpObject)) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!handlingUpload && !discardingRejectedRequest) {
            if (httpObject instanceof HttpRequest request && uploadService.matches(request)) {
                startedAtNanos = System.nanoTime();
                MetricsCollector.instance().requestStarted();
                currentRequest = request;

                AdminWarUploadService.AuthorizationResult authorization = uploadService.authorize(request);
                if (!authorization.allowed()) {
                    discardingRejectedRequest = true;
                    writeResponse(ctx, authorization.statusCode(), false, authorization.message());
                    if (httpObject instanceof LastHttpContent) {
                        discardingRejectedRequest = false;
                    }
                    ReferenceCountUtil.release(msg);
                    return;
                }

                String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
                if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
                    discardingRejectedRequest = true;
                    writeResponse(ctx, 400, false, "Expected multipart/form-data");
                    if (httpObject instanceof LastHttpContent) {
                        discardingRejectedRequest = false;
                    }
                    ReferenceCountUtil.release(msg);
                    return;
                }

                Files.createDirectories(stagingDirectory);
                decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(true), request);
                handlingUpload = true;

                if (HttpUtil.is100ContinueExpected(request)) {
                    ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
                }
            } else {
                ctx.fireChannelRead(msg);
                return;
            }
        }

        if (discardingRejectedRequest) {
            if (httpObject instanceof LastHttpContent) {
                discardingRejectedRequest = false;
            }
            ReferenceCountUtil.release(msg);
            return;
        }

        if (!handlingUpload) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            if (httpObject instanceof HttpContent content) {
                decoder.offer(content);
                drainDecoder();
                if (httpObject instanceof LastHttpContent) {
                    finishUpload(ctx);
                }
            }
        } catch (Exception exception) {
            ErrorLog.log(AdminWarUploadChannelHandler.class.getName(), "Admin WAR upload failed", exception);
            int statusCode = exception instanceof IllegalArgumentException ? 400 : 500;
            writeResponse(ctx, statusCode, false, exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getSimpleName());
            cleanupState(true);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanupState(true);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.io.IOException && !ctx.channel().isActive()) {
            log.debug("Upload client disconnected: {} - {}", ctx.channel().remoteAddress(), cause.getMessage());
        } else if (cause instanceof java.io.IOException
                && cause.getMessage() != null
                && cause.getMessage().toLowerCase().contains("reset")) {
            log.debug("Upload connection reset by peer: {} - {}", ctx.channel().remoteAddress(), cause.getMessage());
        } else {
            ErrorLog.log(AdminWarUploadChannelHandler.class.getName(), "Streaming upload pipeline error", cause);
        }
        cleanupState(true);
        ctx.close();
    }

    private void drainDecoder() throws Exception {
        while (true) {
            HttpData data;
            try {
                data = (HttpData) decoder.next();
            } catch (HttpPostRequestDecoder.EndOfDataDecoderException ignored) {
                break;
            }
            if (data == null) {
                break;
            }
            try {
                if (data instanceof Attribute attribute && attribute.isCompleted()) {
                    if ("contextPath".equals(attribute.getName())) {
                        contextPath = attribute.getValue();
                    }
                } else if (data instanceof FileUpload fileUpload
                        && "file".equals(fileUpload.getName())
                        && fileUpload.isCompleted()) {
                    submittedFileName = fileUpload.getFilename();
                    if (stagedFile == null) {
                        stagedFile = Files.createTempFile(stagingDirectory, "upload-", ".war");
                        if (!fileUpload.renameTo(stagedFile.toFile())) {
                            throw new IllegalStateException("Failed to persist uploaded WAR to staging file");
                        }
                    }
                }
            } finally {
                data.release();
            }
        }
    }

    private void finishUpload(ChannelHandlerContext ctx) throws Exception {
        if (stagedFile == null || !Files.exists(stagedFile)) {
            writeResponse(ctx, 400, false, "No WAR file provided");
            cleanupState(true);
            return;
        }

        AdminWarUploadService.UploadResult result =
                uploadService.handleUploadedWar(stagedFile, submittedFileName, contextPath);
        writeResponse(ctx, result.statusCode(), result.success(), result.message());
        cleanupState(false);
    }

    private void writeResponse(ChannelHandlerContext ctx, int statusCode, boolean success, String message) {
        var response = HttpResponses.json(HttpResponseStatus.valueOf(statusCode),
                "{\"success\":" + success + ",\"message\":\"" + escapeJson(message) + "\"}");

        boolean keepAlive = currentRequest != null && HttpUtil.isKeepAlive(currentRequest);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        }

        long durationNanos = startedAtNanos == 0 ? 0 : System.nanoTime() - startedAtNanos;
        MetricsCollector.instance().requestCompleted(durationNanos, statusCode);
        AccessLog.log(new AccessLogEntry(
                Instant.now(),
                currentRequest != null ? currentRequest.method().name() : "POST",
                currentRequest != null ? currentRequest.uri() : "",
                currentRequest != null ? currentRequest.protocolVersion().text() : HttpVersion.HTTP_1_1.text(),
                statusCode,
                response.content().readableBytes(),
                durationNanos / 1_000_000,
                ctx.channel().remoteAddress() != null ? ctx.channel().remoteAddress().toString() : null,
                currentRequest != null ? currentRequest.headers().get(HttpHeaderNames.USER_AGENT) : null,
                currentRequest != null ? currentRequest.headers().get(HttpHeaderNames.REFERER) : null,
                null));

        if (keepAlive) {
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void cleanupState(boolean deleteStagedFile) {
        if (decoder != null) {
            try {
                decoder.destroy();
            } catch (Exception ignored) {
            }
        }
        if (deleteStagedFile && stagedFile != null) {
            try {
                Files.deleteIfExists(stagedFile);
            } catch (Exception ignored) {
            }
        }
        handlingUpload = false;
        discardingRejectedRequest = false;
        currentRequest = null;
        decoder = null;
        stagedFile = null;
        submittedFileName = null;
        contextPath = null;
        startedAtNanos = 0;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
